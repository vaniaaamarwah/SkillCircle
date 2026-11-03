package service;

import java.util.ArrayList;
import model.Student;

// What the running program needs to REMEMBER while it is open:
//   1. who is logged in right now
//   2. which screens want to be told when data changes
//
// It does NOT hold any student, request or session data any more. That data lives in the
// SQLite database and is read through the DAO classes, so every screen sees the same
// data and nothing is lost when the program closes.
public class AppContext {

    private Student currentStudent;   // null = nobody is logged in

    // Screens register a Runnable here; it runs whenever data changes.
    // This is how tables and dashboard numbers refresh themselves
    // (a simple version of the "Observer" idea).
    private final ArrayList<Runnable> changeListeners = new ArrayList<>();

    // ----- Logged-in student -----
    public void setCurrentStudent(Student student) {
        this.currentStudent = student;
    }

    public Student getCurrentStudent() {
        return currentStudent;
    }

    // The Student ID of the logged-in student, or null when nobody is logged in
    public String getCurrentStudentId() {
        return currentStudent == null ? null : currentStudent.getId();
    }

    // ----- Change notification -----
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void fireDataChanged() {
        // iterate over a copy, in case a listener adds another listener
        for (Runnable listener : new ArrayList<>(changeListeners)) {
            listener.run();
        }
    }
}
