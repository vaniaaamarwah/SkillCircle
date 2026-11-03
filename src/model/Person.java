package model;

// ABSTRACTION: a Person is a general idea, so you cannot create a plain Person.
// Subclasses (like Student) must say what their role and summary are.
public abstract class Person {

    // ENCAPSULATION: private fields, accessed only through methods
    private final String id;      // never changes after creation (it is the "key")
    private String fullName;
    private String email;

    public Person(String id, String fullName, String email) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
    }

    public String getId()       { return id; }
    public String getFullName() { return fullName; }
    public String getEmail()    { return email; }

    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setEmail(String email)       { this.email = email; }

    // Every subclass must provide these (POLYMORPHISM: each class answers in its own way)
    public abstract String getRole();
    public abstract String getSummary();

    // Example: "Demo Student (CS2024001)"
    public String getDisplayName() {
        return fullName + " (" + id + ")";
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
