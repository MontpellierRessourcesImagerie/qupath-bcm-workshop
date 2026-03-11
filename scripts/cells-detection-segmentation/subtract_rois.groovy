def subtract(classToSubtract, classMain, removeOriginal) {
    def annoMain = getAnnotationObjects().find{it.getPathClass() == getPathClass(classMain)}
    def annoToSubtract = getAnnotationObjects().findAll{it.getPathClass() == getPathClass(classToSubtract)}
    
    def roiMain = annoMain.getROI()
    def roisToSubtract = RoiTools.union(annoToSubtract.collect{it.getROI()})
    
    def subtractedROI = RoiTools.combineROIs(roiMain, roisToSubtract, RoiTools.CombineOp.SUBTRACT)
    def subtractedObj = PathObjects.createAnnotationObject(subtractedROI, annoMain.getPathClass())
    addObject(subtractedObj)
    
    if (removeOriginal) {
        QP.removeObjects(annoMain)
        QP.removeObjects(annoToSubtract)
    }
}