/**
 * Compute segmentation metrics for one image and store them in image measurements.
 *
 * Expected workflow:
 * - Run once on one image to validate class names and outputs.
 * - Then run for the whole project.
 * - Export measurements with type "Image".
 */

import org.locationtech.jts.operation.union.UnaryUnionOp

def target_class = "XXX"

// Input classes
def gt_class = "GT_" + target_class
def pred_class = "Pred_" + target_class

// Output classes
def inter_class = "Inter_" + target_class
def union_class = "Union_" + target_class

// Prefix used in exported measurement names
def measurement_prefix = "SegQC "

final double EPS = 1.0e-9

def getAnnotationsByClass = { className ->
    getAnnotationObjects().findAll { obj ->
        def pathClass = obj.getPathClass()
        pathClass != null && pathClass.getName() == className
    }
}

def unionGeometry = { objects ->
    def geoms = objects
        .collect { it.getROI()?.getGeometry() }
        .findAll { it != null && !it.isEmpty() }
    geoms.isEmpty() ? null : UnaryUnionOp.union(geoms)
}

def areaOf = { geometry ->
    geometry == null || geometry.isEmpty() ? 0.0 : geometry.getArea()
}

def safeDivide = { num, den ->
    den <= EPS ? Double.NaN : num / den
}

def gtGeom = unionGeometry(getAnnotationsByClass(gt_class))
def predGeom = unionGeometry(getAnnotationsByClass(pred_class))

if (gtGeom == null && predGeom == null) {
    print("No GT and no prediction annotations found. Nothing to compute.")
    return
}

def interObjects = getAnnotationsByClass(inter_class)
def unionObjects = getAnnotationsByClass(union_class)

def interGeom = unionGeometry(interObjects)
def unionGeom = unionGeometry(unionObjects)

// Fallback if Inter/Union annotations are absent
if (interGeom == null && gtGeom != null && predGeom != null)
    interGeom = gtGeom.intersection(predGeom)
if (unionGeom == null) {
    if (gtGeom != null && predGeom != null)
        unionGeom = gtGeom.union(predGeom)
    else if (gtGeom != null)
        unionGeom = gtGeom
    else if (predGeom != null)
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

def root = getCurrentHierarchy().getRootObject()
def ml = root.getMeasurementList()

def putMetric = { name, value ->
    try {
        ml.put(name, value as double)
    } catch (MissingMethodException e) {
        ml.putMeasurement(name, value as double)
    }
}

putMetric("${measurement_prefix}A_GT", aGT)
putMetric("${measurement_prefix}A_Pred", aPred)
putMetric("${measurement_prefix}A_Inter", aInter)
putMetric("${measurement_prefix}A_Union", aUnion)

putMetric("${measurement_prefix}TP", tp)
putMetric("${measurement_prefix}FP", fp)
putMetric("${measurement_prefix}FN", fn)

putMetric("${measurement_prefix}IoU", iou)
putMetric("${measurement_prefix}Dice", dice)
putMetric("${measurement_prefix}Precision", precision)
putMetric("${measurement_prefix}Recall", recall)
ml.close()

print(
    "Metrics saved (${measurement_prefix.trim()}): " +
    "IoU=${String.format('%.4f', iou)}, " +
    "Dice=${String.format('%.4f', dice)}, " +
    "Precision=${String.format('%.4f', precision)}, " +
    "Recall=${String.format('%.4f', recall)}"
)
