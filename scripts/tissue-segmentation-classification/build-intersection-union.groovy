/**
 * Create intersection/union annotations between two classes.
 * Edit the 4 class names below before running.
 *
 * Expected workflow:
 * - Run once on a single image to validate class names.
 * - Then run for the whole project.
 */

import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.geom.util.PolygonExtracter
import qupath.lib.objects.PathObjects
import qupath.lib.regions.ImagePlane
import qupath.lib.roi.ROIs

// Input classes
def gt_class = "Glomerulus"
def pred_class = "Pred_Glomerus"

// Output classes
def inter_class = "Inter_Target"
def union_class = "Union_Target"

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

def toAreaGeometry = { geometry ->
    if (geometry == null || geometry.isEmpty())
        return null

    def cleaned = geometry.buffer(0)
    if (cleaned == null || cleaned.isEmpty())
        return null

    def polygons = PolygonExtracter.getPolygons(cleaned)
    if (polygons == null || polygons.isEmpty())
        return null

    polygons.size() == 1 ? polygons[0] : UnaryUnionOp.union(polygons)
}

def callStaticIfAvailable = { className, methodName, args ->
    try {
        def cls = Class.forName(className)
        return cls.metaClass.invokeStaticMethod(cls, methodName, args as Object[])
    } catch (ClassNotFoundException ignored) {
        return null
    } catch (MissingMethodException ignored) {
        return null
    } catch (Throwable ignored) {
        return null
    }
}

def createRoiFromGeometry = { geometry ->
    def plane = ImagePlane.getDefaultPlane()

    // QuPath API changed across versions; try geometry conversion methods in order.
    def roi = callStaticIfAvailable("qupath.lib.roi.GeometryTools", "geometryToROI", [geometry, plane])
    if (roi != null)
        return roi

    roi = callStaticIfAvailable("qupath.lib.roi.GeometryTools", "geometryToROI", [geometry])
    if (roi != null)
        return roi

    def attempts = [
        { ROIs.createAreaROI(geometry, plane) },
        { ROIs.createGeometryROI(geometry, plane) },
        { ROIs.createAreaROI(geometry) },
        { ROIs.createGeometryROI(geometry) }
    ]
    for (def attempt : attempts) {
        try {
            roi = attempt()
            if (roi != null)
                return roi
        } catch (MissingMethodException ignored) {
            // Try next overload for compatibility across QuPath versions.
        }
    }
    return null
}

def createPolygonRoi = { xArr, yArr ->
    def plane = ImagePlane.getDefaultPlane()
    def attempts = [
        { ROIs.createPolygonROI(xArr, yArr, plane) },
        { ROIs.createPolygonROI(xArr, yArr) }
    ]
    for (def attempt : attempts) {
        try {
            def roi = attempt()
            if (roi != null)
                return roi
        } catch (MissingMethodException ignored) {
            // Try next overload for compatibility across QuPath versions.
        }
    }
    return null
}

def addAnnotationFromGeometry = { geometry, className ->
    def areaGeometry = toAreaGeometry(geometry)
    if (areaGeometry == null || areaGeometry.isEmpty())
        return false

    def roi = createRoiFromGeometry(areaGeometry)
    if (roi != null) {
        def obj = PathObjects.createAnnotationObject(roi)
        obj.setClassification(className)
        addObject(obj)
        return true
    }

    // Fallback for very old QuPath APIs: add one polygon annotation per polygon.
    def polygons = PolygonExtracter.getPolygons(areaGeometry)
    if (polygons == null || polygons.isEmpty())
        return false

    def created = false
    polygons.each { poly ->
        def coords = poly.getExteriorRing()?.getCoordinates()
        if (coords == null || coords.size() < 4)
            return

        int n = coords.size() - 1 // Last coordinate repeats the first.
        if (n < 3)
            return

        double[] xArr = new double[n]
        double[] yArr = new double[n]
        for (int i = 0; i < n; i++) {
            xArr[i] = coords[i].x
            yArr[i] = coords[i].y
        }

        def polyRoi = createPolygonRoi(xArr, yArr)
        if (polyRoi != null) {
            def obj = PathObjects.createAnnotationObject(polyRoi)
            obj.setClassification(className)
            addObject(obj)
            created = true
        }
    }
    return created
}

def gtObjects = getAnnotationsByClass(gt_class)
def predObjects = getAnnotationsByClass(pred_class)

if (gtObjects.isEmpty()) {
    print("No annotation found for class '${gt_class}'.")
    return
}
if (predObjects.isEmpty()) {
    print("No annotation found for class '${pred_class}'.")
    return
}

def gtGeometry = unionGeometry(gtObjects)
def predGeometry = unionGeometry(predObjects)

if (gtGeometry == null || predGeometry == null) {
    print("Unable to create geometries from input classes.")
    return
}

def interGeometry = gtGeometry.intersection(predGeometry)
def unionGeometryResult = gtGeometry.union(predGeometry)

// Replace old outputs if they exist
selectObjectsByClassification(inter_class)
removeSelectedObjects()
selectObjectsByClassification(union_class)
removeSelectedObjects()

def interCreated = addAnnotationFromGeometry(interGeometry, inter_class)
def unionCreated = addAnnotationFromGeometry(unionGeometryResult, union_class)

print("Done. ${inter_class}: ${interCreated ? 'created' : 'empty'} | ${union_class}: ${unionCreated ? 'created' : 'empty'}")
