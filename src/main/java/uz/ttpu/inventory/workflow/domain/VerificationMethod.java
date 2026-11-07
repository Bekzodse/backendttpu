package uz.ttpu.inventory.workflow.domain;

public enum VerificationMethod {
  E_IMZO,
  TOTP,
  EITHER;

  public boolean isSatisfiedBy(VerificationMethod actual) {
    if (actual == null || actual == EITHER) {
      return false;
    }
    return this == EITHER || this == actual;
  }
}
