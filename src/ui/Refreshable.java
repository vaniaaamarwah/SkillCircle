package ui;

// Anything that can reload what it shows from the shared data.
// Every screen implements this, so MainFrame can refresh them all in the same way
// (POLYMORPHISM: each screen refreshes itself differently).
public interface Refreshable {
    void refresh();
}
