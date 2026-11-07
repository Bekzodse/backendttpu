package uz.ttpu.inventory.receipt.domain;

import java.util.UUID;

public class DuplicateSerialNumberException extends IllegalStateException {

    public DuplicateSerialNumberException(UUID categoryId, String serialNumber) {
        super("Serial number '" + serialNumber + "' already exists in category " + categoryId);
    }
}
