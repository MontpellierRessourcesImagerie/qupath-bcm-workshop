/**
 * Create visual QC overlays for detection errors.
 *
 * Overlays:
 * - TP_Pred: matched predictions
 * - FP_Pred: unmatched predictions
 * - FN_GT: unmatched GT
 * - OverSeg_GT: GT overlapped by multiple predictions
 * - UnderSeg_Pred: prediction overlapped by multiple GT
 *
 * Matching rule:
 * - One-to-one greedy matching
 * - Prediction centroid inside GT, or GT centroid inside prediction,
 *   or centroid distance <= centroid_distance_threshold
 *
 * Group parents are annotations; individual overlays are detections so they
 * stay grouped in Hierarchy without cluttering the Annotations list.
 */

import qupath.lib.objects.PathObjects

// Input classes
def gt_class = "GT"
def pred_class = "BuiltIn"

// Unit is the same as ROI coordinates (usually pixels in QuPath image space).
def centroid_distance_threshold = 12.0

// Significance thresholds used only for over/under-segmentation diagnostics.
def over_under_min_overlap_pred = 0.10
def over_under_min_overlap_gt = 0.10
def over_under_min_pair_iou = 0.05

// Overlay classes
def tp_pred_class = "TP_Pred"
def fp_pred_class = "FP_Pred"
def fn_gt_class = "FN_GT"
def over_seg_gt_class = "OverSeg_GT"
def under_seg_pred_class = "UnderSeg_Pred"
def overlay_parent_class = "SegQC_OverlayParent"
def tp_parent_class = "SegQC_TP_Pred_Parent"
def fp_parent_class = "SegQC_FP_Pred_Parent"
def fn_parent_class = "SegQC_FN_GT_Parent"
def over_seg_parent_class = "SegQC_OverSeg_GT_Parent"
def under_seg_parent_class = "SegQC_UnderSeg_Pred_Parent"

def replace_previous_overlays = true

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
        if (areaOf(geom) <= EPS)
            return
        eval << [idx: idx, obj: obj, geom: geom]
    }
    eval
}

def centroidDistance = { a, b ->
    def ca = a.getCoordinate()
    def cb = b.getCoordinate()
    double dx = ca.x - cb.x
    double dy = ca.y - cb.y
    Math.sqrt(dx * dx + dy * dy)
}

def createOverlayParent = {
    createFullImageAnnotation(true)
    def selected = QP.getSelectedObjects()
    if (selected == null || selected.size() != 1) {
        print("Unable to create or select a full-image annotation for overlays.")
        return null
    }

    def parent = selected[0]
    parent.setClassification(overlay_parent_class)
    try {
        parent.setName(overlay_parent_class)
    } catch (Throwable ignored) {
        // Name is optional.
    }
    return parent
}

def buildOverlayFromObject = { obj, className ->
    def roi = obj?.getROI()
    if (roi == null)
        return null
    def overlay = PathObjects.createDetectionObject(roi)
    overlay.setClassification(className)
    overlay
}

def buildGroupParent = { parentRoi, className, name ->
    if (parentRoi == null)
        return null
    def groupParent = PathObjects.createAnnotationObject(parentRoi)
    groupParent.setClassification(className)
    try {
        groupParent.setName(name)
    } catch (Throwable ignored) {
        // Name is optional.
    }
    groupParent
}

if (replace_previous_overlays) {
    [
        overlay_parent_class,
        tp_parent_class,
        fp_parent_class,
        fn_parent_class,
        over_seg_parent_class,
        under_seg_parent_class,
        tp_pred_class,
        fp_pred_class,
        fn_gt_class,
        over_seg_gt_class,
        under_seg_pred_class
    ].each { cls ->
        selectObjectsByClassification(cls)
        removeSelectedObjects()
    }
}

def gtEval = toEvalObjects(getObjectsByClass(gt_class))
def predEval = toEvalObjects(getObjectsByClass(pred_class))

if (gtEval.isEmpty() && predEval.isEmpty()) {
    print("No valid GT and no valid prediction objects found. Nothing to do.")
    return
}

int nGT = gtEval.size()
int nPred = predEval.size()

def gtOverlapSets = (0..<nGT).collect { [] as Set }
def predOverlapSets = (0..<nPred).collect { [] as Set }
def gtAreas = gtEval.collect { areaOf(it.geom) }
def predAreas = predEval.collect { areaOf(it.geom) }
def gtCentroids = gtEval.collect { it.geom.getCentroid() }
def predCentroids = predEval.collect { it.geom.getCentroid() }
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
candidates.each { c ->
    if (!matchedGT.contains(c.gi) && !matchedPred.contains(c.pi)) {
        matchedGT << c.gi
        matchedPred << c.pi
    }
}

def overlayParent = createOverlayParent()
if (overlayParent == null)
    return

def overlayGroups = [
    (tp_pred_class): [],
    (fp_pred_class): [],
    (fn_gt_class): [],
    (over_seg_gt_class): [],
    (under_seg_pred_class): []
]
int tpCount = 0
int fpCount = 0
int fnCount = 0
int overSegGTCount = 0
int underSegPredCount = 0

for (int pi = 0; pi < nPred; pi++) {
    def predObj = predEval[pi].obj
    if (matchedPred.contains(pi)) {
        def overlay = buildOverlayFromObject(predObj, tp_pred_class)
        if (overlay != null) {
            overlayGroups[tp_pred_class] << overlay
            tpCount++
        }
    } else {
        def overlay = buildOverlayFromObject(predObj, fp_pred_class)
        if (overlay != null) {
            overlayGroups[fp_pred_class] << overlay
            fpCount++
        }
    }

    if (predOverlapSets[pi].size() > 1) {
        def overlay = buildOverlayFromObject(predObj, under_seg_pred_class)
        if (overlay != null) {
            overlayGroups[under_seg_pred_class] << overlay
            underSegPredCount++
        }
    }
}

for (int gi = 0; gi < nGT; gi++) {
    def gtObj = gtEval[gi].obj
    if (!matchedGT.contains(gi)) {
        def overlay = buildOverlayFromObject(gtObj, fn_gt_class)
        if (overlay != null) {
            overlayGroups[fn_gt_class] << overlay
            fnCount++
        }
    }

    if (gtOverlapSets[gi].size() > 1) {
        def overlay = buildOverlayFromObject(gtObj, over_seg_gt_class)
        if (overlay != null) {
            overlayGroups[over_seg_gt_class] << overlay
            overSegGTCount++
        }
    }
}

def groupSpecs = [
    [overlayClass: tp_pred_class, parentClass: tp_parent_class, name: tp_pred_class],
    [overlayClass: fp_pred_class, parentClass: fp_parent_class, name: fp_pred_class],
    [overlayClass: fn_gt_class, parentClass: fn_parent_class, name: fn_gt_class],
    [overlayClass: over_seg_gt_class, parentClass: over_seg_parent_class, name: over_seg_gt_class],
    [overlayClass: under_seg_pred_class, parentClass: under_seg_parent_class, name: under_seg_pred_class]
]

def groupParents = []
groupSpecs.each { spec ->
    def children = overlayGroups[spec.overlayClass]
    if (children == null || children.isEmpty())
        return
    def groupParent = buildGroupParent(overlayParent.getROI(), spec.parentClass, spec.name)
    if (groupParent == null)
        return
    groupParent.addChildObjects(children)
    groupParents << groupParent
}

if (!groupParents.isEmpty())
    overlayParent.addChildObjects(groupParents)

print(
    "Overlay QC done (${pred_class} vs ${gt_class}) -> " +
    "N_GT=${nGT}, N_Pred=${nPred}, TP=${tpCount}, FP=${fpCount}, FN=${fnCount}, " +
    "OverSegGT=${overSegGTCount}, UnderSegPred=${underSegPredCount}, " +
    "CentroidDist=${centroid_distance_threshold}"
)
