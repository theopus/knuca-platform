package com.theopus.entity.schedule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Created by Oleksandr_Tkachov on 9/15/2017.
 */
@Entity(name = "group_")
public class Group {

    @Id@GeneratedValue(generator = "increment")
    @GenericGenerator(name = "increment", strategy = "increment")
    private Long id;
    @Column(unique = true, name = "name")
    private String name;

    @OneToMany(mappedBy = "group", cascade = {CascadeType.REMOVE, CascadeType.DETACH},
            fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<Curriculum> curriculums = new HashSet<>();

    public Group() {
    }

    public Group(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return Objects.equals(name, group.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Curriculum> getCurriculums() {
        return curriculums;
    }

    public void setCurriculums(Set<Curriculum> curriculums) {
        this.curriculums = curriculums;
    }

    @Override
    public String toString() {
        return "Group{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
