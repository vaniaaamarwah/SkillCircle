package service;

import dao.StudentDAO;
import java.util.ArrayList;
import java.util.List;
import model.Skill;
import model.SkillMatch;
import model.Student;

// RULE-BASED matching (this is NOT artificial intelligence).
//
// Two students A and B are a MUTUAL MATCH when:
//   1. A offers at least one skill that B wants, AND
//   2. B offers at least one skill that A wants.
// The matches are recalculated from the skills in the database every time, so they always
// reflect the latest changes; there is no stored list that could go out of date.
public class MatchingService {

    private final StudentDAO studentDAO;

    public MatchingService(StudentDAO studentDAO) {
        this.studentDAO = studentDAO;
    }

    public List<SkillMatch> findMatches(String myId) {
        List<SkillMatch> matches = new ArrayList<>();
        Student me = studentDAO.findByStudentId(myId);
        if (me == null) {
            return matches;
        }
        for (Student other : studentDAO.findAll()) {
            if (other.getId().equalsIgnoreCase(me.getId())) {
                continue;   // no self-matches
            }
            SkillMatch match = compare(me, other);
            if (match.isMutual()) {
                matches.add(match);   // one entry per student, so no duplicates
            }
        }
        // more shared skills first, then by name
        matches.sort((a, b) -> {
            int sizeA = a.getSkillsICanTeach().size() + a.getSkillsTheyTeach().size();
            int sizeB = b.getSkillsICanTeach().size() + b.getSkillsTheyTeach().size();
            if (sizeA != sizeB) {
                return sizeB - sizeA;
            }
            return a.getOther().getFullName().compareToIgnoreCase(b.getOther().getFullName());
        });
        return matches;
    }

    public SkillMatch compare(Student me, Student other) {
        List<Skill> iCanTeach = new ArrayList<>();
        for (Skill skill : me.getOfferedSkills()) {
            if (other.wantsSkill(skill)) {
                iCanTeach.add(skill);
            }
        }
        List<Skill> theyTeach = new ArrayList<>();
        for (Skill skill : other.getOfferedSkills()) {
            if (me.wantsSkill(skill)) {
                theyTeach.add(skill);
            }
        }
        return new SkillMatch(other, iCanTeach, theyTeach);
    }
}
