package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// INHERITANCE: a Student "is a" Person, so it reuses id, name and email.
// A Student adds profile data: department, year, bio, skills and availability.
public class Student extends Person {

    // Fixed choices shared by all screens
    public static final String[] DEPARTMENTS = {
            "Computer Science (CSE)",
            "Information Technology (IT)",
            "Electronics (ECE)",
            "Electrical (EE)",
            "Mechanical (ME)",
            "Civil (CE)"
    };
    public static final String[] YEARS = {"1st Year", "2nd Year", "3rd Year", "4th Year"};
    public static final String[] WEEK_DAYS = {
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };
    public static final String[] TIME_SLOTS = {
            "Morning (8 AM - 12 PM)", "Afternoon (12 PM - 4 PM)", "Evening (4 PM - 8 PM)"
    };
    public static final String MODE_ONLINE = "Online";
    public static final String MODE_OFFLINE = "Offline";

    private String department;
    private String yearOfStudy;
    private String bio;

    private final ArrayList<Skill> offeredSkills = new ArrayList<>();   // skills I can teach
    private final ArrayList<Skill> wantedSkills = new ArrayList<>();    // skills I want to learn
    private final ArrayList<String> availableDays = new ArrayList<>();
    private final ArrayList<String> timeSlots = new ArrayList<>();
    private String learningMode = MODE_ONLINE;

    // true for the built-in demo students, false for students created by the user
    private boolean sample = false;

    // Favourites are stored as Student IDs, not as copies of Student objects,
    // so no student record is ever duplicated.
    private final ArrayList<String> savedStudentIds = new ArrayList<>();

    public Student(String studentId, String fullName, String email,
                   String department, String yearOfStudy, String bio) {
        super(studentId, fullName, email);   // calls the Person constructor
        this.department = department;
        this.yearOfStudy = yearOfStudy;
        this.bio = bio;
    }

    // ----- Sample (demo) data flag -----
    public boolean isSample()      { return sample; }
    public void markAsSample()     { this.sample = true; }

    // ----- Polymorphism: our own answers to Person's abstract methods -----
    @Override
    public String getRole() {
        return "Student";
    }

    @Override
    public String getSummary() {
        return getFullName() + " | " + department + " | " + yearOfStudy;
    }

    // ----- Simple getters and setters -----
    public String getDepartment()  { return department; }
    public String getYearOfStudy() { return yearOfStudy; }
    public String getBio()         { return bio; }
    public String getLearningMode() { return learningMode; }

    public void setDepartment(String department)   { this.department = department; }
    public void setYearOfStudy(String yearOfStudy) { this.yearOfStudy = yearOfStudy; }
    public void setBio(String bio)                 { this.bio = bio; }

    // ----- Skills -----
    // The getters return READ-ONLY views, so outside code cannot change the
    // lists directly. It must use the add/remove methods below.
    public List<Skill> getOfferedSkills() { return Collections.unmodifiableList(offeredSkills); }
    public List<Skill> getWantedSkills()  { return Collections.unmodifiableList(wantedSkills); }

    // Returns false if the skill is already in the list
    public boolean addOfferedSkill(Skill skill) {
        if (offeredSkills.contains(skill)) {
            return false;
        }
        offeredSkills.add(skill);
        return true;
    }

    public boolean removeOfferedSkill(Skill skill) {
        return offeredSkills.remove(skill);
    }

    public boolean addWantedSkill(Skill skill) {
        if (wantedSkills.contains(skill)) {
            return false;
        }
        wantedSkills.add(skill);
        return true;
    }

    public boolean removeWantedSkill(Skill skill) {
        return wantedSkills.remove(skill);
    }

    public boolean offersSkill(Skill skill) { return offeredSkills.contains(skill); }
    public boolean wantsSkill(Skill skill)  { return wantedSkills.contains(skill); }

    // ----- Availability -----
    public List<String> getAvailableDays() { return Collections.unmodifiableList(availableDays); }
    public List<String> getTimeSlots()     { return Collections.unmodifiableList(timeSlots); }

    public void setAvailability(List<String> days, List<String> slots, String mode) {
        availableDays.clear();
        availableDays.addAll(days);
        timeSlots.clear();
        timeSlots.addAll(slots);
        learningMode = mode;
    }

    // ----- Saved (favourite) students -----
    public List<String> getSavedStudentIds() {
        return Collections.unmodifiableList(savedStudentIds);
    }

    public boolean isSaved(String studentId) {
        for (String savedId : savedStudentIds) {
            if (savedId.equalsIgnoreCase(studentId)) {
                return true;
            }
        }
        return false;
    }

    // Returns false if already saved
    public boolean saveStudent(String studentId) {
        if (isSaved(studentId)) {
            return false;
        }
        savedStudentIds.add(studentId);
        return true;
    }

    public void unsaveStudent(String studentId) {
        savedStudentIds.removeIf(savedId -> savedId.equalsIgnoreCase(studentId));
    }
}
