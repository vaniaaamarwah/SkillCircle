package model;

import java.util.ArrayList;
import java.util.List;

// The RESULT of rule-based matching between me and one other student.
//   skillsIcanTeach = skills I offer that the other student wants
//   skillsTheyTeach = skills the other student offers that I want
// A match is MUTUAL only when both lists are non-empty.
public class SkillMatch {

    private final Student other;
    private final List<Skill> skillsICanTeach;
    private final List<Skill> skillsTheyTeach;

    public SkillMatch(Student other, List<Skill> skillsICanTeach, List<Skill> skillsTheyTeach) {
        this.other = other;
        this.skillsICanTeach = new ArrayList<>(skillsICanTeach);
        this.skillsTheyTeach = new ArrayList<>(skillsTheyTeach);
    }

    public Student getOther()                { return other; }
    public List<Skill> getSkillsICanTeach()  { return skillsICanTeach; }
    public List<Skill> getSkillsTheyTeach()  { return skillsTheyTeach; }

    public boolean isMutual() {
        return !skillsICanTeach.isEmpty() && !skillsTheyTeach.isEmpty();
    }

    // A short, plain-English reason for the match
    public String getExplanation() {
        return "You offer " + join(skillsICanTeach) + ", which " + other.getFullName()
                + " wants; " + other.getFullName() + " offers " + join(skillsTheyTeach)
                + ", which you want.";
    }

    private static String join(List<Skill> skills) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < skills.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(skills.get(i).getName());
        }
        return text.toString();
    }
}
