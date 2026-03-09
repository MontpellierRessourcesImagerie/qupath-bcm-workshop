selectDetections();
addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER", "NUCLEUS_CELL_RATIO")
def worms = getDetectionObjects().findAll{it.getPathClass() == null}
for (w: worms) {
    m = w.getMeasurements()
    min_axis = m.get("Min diameter px")
    max_axis = m.get("Max diameter px")
    ratio = min_axis / max_axis
    m.put("Axis ratio", ratio)
}