package uz.ttpu.inventory.asset.domain;

public class IllegalStateTransitionException extends IllegalStateException {

  public IllegalStateTransitionException(ItemState currentState, ItemEvent event) {
    super("Illegal item state transition from state " + currentState + " via event " + event);
  }

  public IllegalStateTransitionException(String message) {
    super(message);
  }
}
