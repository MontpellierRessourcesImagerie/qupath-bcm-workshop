given_class = "BuiltIn";

def attribute_class(object, class_name) {
    object.setClassification(class_name);
    for (c: object.getChildObjects()) {
        attribute_class(c, given_class);
    }
}

def root_objs = QP.getSelectedObjects();
for (obj: root_objs) {
    attribute_class(obj, null);
}