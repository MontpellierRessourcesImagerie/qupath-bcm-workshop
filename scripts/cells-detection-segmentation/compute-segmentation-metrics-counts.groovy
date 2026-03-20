/**
 * Compute segmentation QC metrics for one image.
 * Dedicated version for object-count QC (TP/FP/FN in number of nuclei).
 *
 * Count matching rule:
 * - One-to-one greedy matching
 * - Prediction centroid inside GT, or GT centroid inside prediction,
 *   or centroid distance <= centroid_distance_threshold
 */

import org.locationtech.jts.operation.union.UnaryUnionOp

// Input classes
def gt_class = "GT"
def pred_class = "BuiltIn"

// Optional precomputed classes from build-intersection-union.groovy
def inter_class = "Inter_BuiltIn"
def union_class = "Union_BuiltIn"

// Prefix used in exported measurement names
def measurement_prefix = "SegQC BuiltIn "

// Unit is the same as ROI coordinates (usually pixels in QuPath image space).
def centroid_distance_threshold = 12.0

// Significance thresholds used only for over/under-segmentation diagnostics.
def over_under_min_overlap_pred = 0.10
def over_under_min_overlap_gt = 0.10
def over_under_min_pair_iou = 0.05

final double EPS = 1.0e-9

def getObjectsByClass = { className ->
    def all = []
    all.addAll(getDetectionObjects())
    all.addAll(getAnnotationObjects())
    all.findAll { obj ->
        def pathClass = obj.getPathClass()
        pathClass != null && pathClass.getName() == className
    }
}

def getAnnotationsByClass = { className ->
    getAnnotationObjects().findAll { obj ->
        def pathClass = obj.getPathClass()
        pathClass != null && pathClass.getName() == className
    }
}

def areaOf = { geometry ->
    geometry == null || geometry.isEmpty() ? 0.0 : geometry.getArea()
}

def safeDivide = { num, den ->
    den <= EPS ? Double.NaN : num / den
}

def toEvalObjects = { objects ->
    def eval = []
    objects.eachWithIndex { obj, idx ->
        def roi = obj?.getROI()
        if (roi == null)
            return
        def geom = roi.getGeometry()
        if (geom == null || geom.isEmpty())
            return
        try {
            geom = geom.buffer(0)
        } catch (Throwable ignored) {
            // Keep raw geometry if cleaning fails.
        }
        if (geom == null || geom.isEmpty())
            return
        double a = areaOf(geom)
        if (a <= EPS)
            return
        eval << [idx: idx, obj: obj, geom: geom, area: a]
    }
    eval
}

def unionGeometryFromGeoms = { geoms ->
    def valid = geoms.findAll { it != null && !it.isEmpty() }
    valid.isEmpty() ? null : UnaryUnionOp.union(valid)
}

def centroidDistance = { a, b ->
    def ca = a.getCoordinate()
    def cb = b.getCoordinate()
    double dx = ca.x - cb.x
    double dy = ca.y - cb.y
    Math.sqrt(dx * dx + dy * dy)
}

def gtEval = toEvalObjects(getObjectsByClass(gt_class))
def predEval = toEvalObjects(getObjectsByClass(pred_class))

if (gtEval.isEmpty() && predEval.isEmpty()) {
    print("No valid GT and no valid prediction objects found. Nothing to compute.")
    return
}

def gtGeom = unionGeometryFromGeoms(gtEval.collect { it.geom })
def predGeom = unionGeometryFromGeoms(predEval.collect { it.geom })

def interGeom = unionGeometryFromGeoms(getAnnotationsByClass(inter_class).collect { it.getROI()?.getGeometry() })
def unionGeom = unionGeometryFromGeoms(getAnnotationsByClass(union_class).collect { it.getROI()?.getGeometry() })

if (interGeom == null && gtGeom != null && predGeom != null)
    interGeom = gtGeom.intersection(predGeom)
if (unionGeom == null) {
    if (gtGeom != null && predGeom != null)
        unionGeom = gtGeom.union(predGeom)
    else if (gtGeom != null)
        unionGeom = gtGeom
    else
        unionGeom = predGeom
}

double aGT = areaOf(gtGeom)
double aPred = areaOf(predGeom)
double aInter = areaOf(interGeom)
double aUnion = areaOf(unionGeom)

double tp = aInter
double fp = Math.max(0.0, aPred - aInter)
double fn = Math.max(0.0, aGT - aInter)

double iou = safeDivide(aInter, aUnion)
double dice = safeDivide(2.0 * aInter, aGT + aPred)
double precision = safeDivide(tp, tp + fp)
double recall = safeDivide(tp, tp + fn)

int nGT = gtEval.size()
int nPred = predEval.size()

def gtOverlapSets = (0..<nGT).collect { [] as Set }
def predOverlapSets = (0..<nPred).collect { [] as Set }
def gtHasCandidate = (0..<nGT).collect { false }
def predHasCandidate = (0..<nPred).collect { false }
def gtCentroids = gtEval.collect { it.geom.getCentroid() }
def predCentroids = predEval.collect { it.geom.getCentroid() }
def gtAreas = gtEval.collect { it.area as double }
def predAreas = predEval.collect { it.area as double }
def candidates = []

for (int gi = 0; gi < nGT; gi++) {
    def g = gtEval[gi].geom
    def gEnv = g.getEnvelopeInternal()
    for (int pi = 0; pi < nPred; pi++) {
        def p = predEval[pi].geom

        boolean predCenterInGT = false
        boolean gtCenterInPred = false
        try {
            predCenterInGT = g.covers(predCentroids[pi])
        } catch (Throwable ignored) {
            predCenterInGT = g.intersects(predCentroids[pi])
        }
        try {
            gtCenterInPred = p.covers(gtCentroids[gi])
        } catch (Throwable ignored) {
            gtCenterInPred = p.intersects(gtCentroids[gi])
        }

        double centerDist = centroidDistance(gtCentroids[gi], predCentroids[pi])
        boolean centroidsClose = centerDist <= centroid_distance_threshold

        double iouPair = 0.0
        if (gEnv.intersects(p.getEnvelopeInternal())) {
            def interPair = g.intersection(p)
            double aInterPair = areaOf(interPair)
            if (aInterPair > EPS) {
                double aUnionPair = areaOf(g.union(p))
                iouPair = safeDivide(aInterPair, aUnionPair)
                if (Double.isNaN(iouPair))
                    iouPair = 0.0

                double fracPred = safeDivide(aInterPair, predAreas[pi])
                double fracGT = safeDivide(aInterPair, gtAreas[gi])
                boolean significantForOverUnder =
                    (!Double.isNaN(fracPred) && fracPred >= over_under_min_overlap_pred) ||
                    (!Double.isNaN(fracGT) && fracGT >= over_under_min_overlap_gt) ||
                    iouPair >= over_under_min_pair_iou

                if (significantForOverUnder) {
                    gtOverlapSets[gi] << pi
                    predOverlapSets[pi] << gi
                }
            }
        }

        boolean eligible = predCenterInGT || gtCenterInPred || centroidsClose
        if (!eligible)
            continue

        gtHasCandidate[gi] = true
        predHasCandidate[pi] = true

        double score =
            (predCenterInGT ? 10.0 : 0.0) +
            (gtCenterInPred ? 5.0 : 0.0) +
            (centroidsClose ? 2.0 : 0.0) +
            iouPair

        candidates << [gi: gi, pi: pi, iou: iouPair, score: score]
    }
}

candidates.sort { a, b ->
    int s = (b.score <=> a.score)
    s != 0 ? s : (b.iou <=> a.iou)
}

def matchedGT = [] as Set
def matchedPred = [] as Set
double matchedIoUSum = 0.0
int tpCount = 0

candidates.each { c ->
    if (!matchedGT.contains(c.gi) && !matchedPred.contains(c.pi)) {
        matchedGT << c.gi
        matchedPred << c.pi
        tpCount++
        matchedIoUSum += (c.iou as double)
    }
}

int fpCount = Math.max(0, nPred - tpCount)
int fnCount = Math.max(0, nGT - tpCount)

int fpNoOverlapCount = 0
int fpNoCandidateCount = 0
int fpCompetingCount = 0
for (int pi = 0; pi < nPred; pi++) {
    if (matchedPred.contains(pi))
        continue
    if (predOverlapSets[pi].isEmpty())
        fpNoOverlapCount++
    else if (!predHasCandidate[pi])
        fpNoCandidateCount++
    else
        fpCompetingCount++
}

int fnNoOverlapCount = 0
int fnNoCandidateCount = 0
int fnCompetingCount = 0
for (int gi = 0; gi < nGT; gi++) {
    if (matchedGT.contains(gi))
        continue
    if (gtOverlapSets[gi].isEmpty())
        fnNoOverlapCount++
    else if (!gtHasCandidate[gi])
        fnNoCandidateCount++
    else
        fnCompetingCount++
}

int overSegGTCount = gtOverlapSets.count { it.size() > 1 }
int underSegPredCount = predOverlapSets.count { it.size() > 1 }

double precisionCount = safeDivide(tpCount, tpCount + fpCount)
double recallCount = safeDivide(tpCount, tpCount + fnCount)
double f1Count = safeDivide(2.0 * precisionCount * recallCount, precisionCount + recallCount)
double meanMatchedIoU = safeDivide(matchedIoUSum, tpCount)

def root = getCurrentHierarchy().getRootObject()
def ml = root.getMeasurementList()

def clearMetricsWithPrefix = { prefix ->
    def names = []
    try {
        names.addAll(ml.getNames())
    } catch (Throwable ignored) {
        return
    }
    names.findAll { it != null && it.startsWith(prefix) }.each { name ->
        try {
            ml.remove(name)
        } catch (Throwable ignored) {
            // Ignore removal failures for compatibility across QuPath versions.
        }
    }
}

def putMetric = { name, value ->
    try {
        ml.put(name, value as double)
    } catch (MissingMethodException e) {
        ml.putMeasurement(name, value as double)
    }
}

clearMetricsWithPrefix(measurement_prefix)

putMetric("${measurement_prefix}A_GT", aGT)
putMetric("${measurement_prefix}A_Pred", aPred)
putMetric("${measurement_prefix}A_Inter", aInter)
putMetric("${measurement_prefix}A_Union", aUnion)

putMetric("${measurement_prefix}TP", tp)
putMetric("${measurement_prefix}FP", fp)
putMetric("${measurement_prefix}FN", fn)

putMetric("${measurement_prefix}N_GT", nGT)
putMetric("${measurement_prefix}N_Pred", nPred)
putMetric("${measurement_prefix}TP_Count", tpCount)
putMetric("${measurement_prefix}FP_Count", fpCount)
putMetric("${measurement_prefix}FN_Count", fnCount)
putMetric("${measurement_prefix}FP_NoOverlap_Count", fpNoOverlapCount)
putMetric("${measurement_prefix}FP_NoCandidate_Count", fpNoCandidateCount)
putMetric("${measurement_prefix}FP_Competing_Count", fpCompetingCount)
putMetric("${measurement_prefix}FN_NoOverlap_Count", fnNoOverlapCount)
putMetric("${measurement_prefix}FN_NoCandidate_Count", fnNoCandidateCount)
putMetric("${measurement_prefix}FN_Competing_Count", fnCompetingCount)
putMetric("${measurement_prefix}OverSeg_GT_Count", overSegGTCount)
putMetric("${measurement_prefix}UnderSeg_Pred_Count", underSegPredCount)
putMetric("${measurement_prefix}Precision_Count", precisionCount)
putMetric("${measurement_prefix}Recall_Count", recallCount)
putMetric("${measurement_prefix}F1_Count", f1Count)
putMetric("${measurement_prefix}MeanIoU_MatchedPairs", meanMatchedIoU)
putMetric("${measurement_prefix}Centroid_Distance_Threshold", centroid_distance_threshold)

putMetric("${measurement_prefix}IoU", iou)
putMetric("${measurement_prefix}Dice", dice)
putMetric("${measurement_prefix}Precision", precision)
putMetric("${measurement_prefix}Recall", recall)

ml.close()

print(
    "Metrics saved (${measurement_prefix.trim()}): " +
    "TP=${tpCount}, FP=${fpCount}, FN=${fnCount}, " +
    "OverSegGT=${overSegGTCount}, UnderSegPred=${underSegPredCount}, " +
    "CentroidDist=${centroid_distance_threshold}, " +
    "IoU=${String.format('%.4f', iou)}, Dice=${String.format('%.4f', dice)}"
)
