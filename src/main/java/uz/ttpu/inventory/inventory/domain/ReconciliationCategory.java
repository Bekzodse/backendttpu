package uz.ttpu.inventory.inventory.domain;

public enum ReconciliationCategory {
    JOYIDA("Joyida"),
    BOSHQA_XONADA_TOPILDI("Boshqa xonada topildi"),
    TOPILMADI("Topilmadi"),
    ORTIQCHA("Ortiqcha");

    private final String label;

    ReconciliationCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
