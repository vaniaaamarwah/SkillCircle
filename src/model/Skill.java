package model;

// One skill, such as "Java" or "Graphic Design".
// Two skills are equal when their names match, ignoring upper/lower case,
// so "java" and "Java" are treated as the same skill.
public class Skill {

    private final String name;

    public Skill(String name) {
        this.name = name.trim();
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Skill)) {
            return false;
        }
        return name.equalsIgnoreCase(((Skill) other).name);
    }

    // equals() and hashCode() must always be written together
    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
