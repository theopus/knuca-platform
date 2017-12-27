package com.theopus.parser.obj.line;

import com.theopus.entity.schedule.*;
import com.theopus.entity.schedule.enums.LessonOrder;
import com.theopus.entity.schedule.enums.LessonType;
import com.theopus.parser.exceptions.IllegalPdfException;
import com.theopus.parser.obj.Patterns;
import com.theopus.parser.obj.roomdate.RoomDateBrackets;
import com.theopus.parser.obj.sheets.DaySheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public abstract class LessonLine {

    private static final Logger LOG = LoggerFactory.getLogger(LessonLine.class);

    protected DaySheet parent;
    protected RoomDateBrackets child;

    private String line;
    private Map<Pattern, Integer> lessonOrderMap;
    private Pattern practPattern;
    private Pattern labPattern;
    private Pattern lecPattern;
    private Pattern facPattern;

    private Pattern teacherPattern;
    private LessonOrder previousOrder;


    private String normalize(String line) {
        return line.replaceAll("\n", " ");
    }

    protected String getGetSplits(boolean isLeft) {
        String[] split = line.split(";");
        if (split.length <= 1) {
            //ToDo add to Throw message parent info(Sheet info (Group, Date, etc.))
            throw new IllegalPdfException("Lesson line '" + line + "' does not contains ';' splitting symbol.");
        }

        if (isLeft) {
            return split[0];
        } else {
            return Arrays.stream(split).skip(1).reduce(String::concat).get();
        }
    }

    public abstract List<Curriculum> parse();

    public LessonOrder parseOrder() {
        LessonOrder lessonOrder = Arrays.stream(LessonOrder.values())
                .filter(l -> (lessonOrderMap.entrySet().stream()
                        .filter(e -> e.getKey().matcher(line).find())
                        .findFirst().orElse(new AbstractMap.SimpleEntry<>(null, 0)).getValue() == l.ordinal()))
                .findFirst().orElse(LessonOrder.NONE);
        if (lessonOrder.ordinal() == 0) {
            if (previousOrder != null && !previousOrder.equals(LessonOrder.NONE)) {
                return previousOrder;
            } else {
                throw new IllegalPdfException("Lesson line " + line + "not contains any info about order");
            }
        } else {
            return lessonOrder;
        }
    }

    protected Subject parseSubject() {
        String leftSide = getGetSplits(true);
        String[] leftsideArr = leftSide.split(" ");
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 1; i < leftsideArr.length - 1; i++) {
            stringBuilder.append(leftsideArr[i]).append(" ");
        }

        return new Subject(stringBuilder.toString().trim());
    }

    protected LessonType parseLessonType() {
        Matcher m = practPattern.matcher(line.toLowerCase());
        if (m.find()) {
            return LessonType.PRACTICE;
        }
        m = labPattern.matcher(line.toLowerCase());
        if (m.find()) {
            return LessonType.LAB;
        }
        m = lecPattern.matcher(line.toLowerCase());
        if (m.find()) {
            return LessonType.LECTURE;
        }
        m = facPattern.matcher(line.toLowerCase());
        if (m.find()) {
            return LessonType.FACULTY;
        }
        throw new IllegalPdfException("Lesson line '" + line + "' does not have lesson type." + "Parent: " + parent);
    }

    protected Set<Teacher> parseTeachers() {
        Matcher m = teacherPattern.matcher(line);
        Set<Teacher> result = new HashSet<>();
        while (m.find()) {
            String teacherName = m.group();
            result.add(new Teacher(teacherName));
        }
        return result;
    }

    protected Set<Group> parseGroups() {
        //TODO: add group parsing avilability;
        return null;
    }

    protected Set<Circumstance> parseCircumstances() {
        child.prepare(getGetSplits(false), parseOrder());
        return child.parseCircumstacnes();
    }

    public LessonLine prepare(String line) {
        this.line = normalize(line);
        return this;
    }

    public LessonLine prepare(String line, LessonOrder previousOrder) {
        this.line = normalize(line);
        this.previousOrder = previousOrder;
        return this;
    }

    public LessonLine parent(DaySheet daySheet) {
        this.parent = daySheet;
        return this;
    }

    public LessonLine child(RoomDateBrackets roomDateBrackets) {
        LessonLine.this.child = roomDateBrackets;
        roomDateBrackets.parent(this);
        return this;
    }

    public class Builder {

        public LessonLine build() {
            return LessonLine.this;
        }

        public Builder defaultPatterns() {
            practPattern = Pattern.compile(Patterns.LessonLine.PRACT, Pattern.DOTALL | Pattern.MULTILINE);
            labPattern = Pattern.compile(Patterns.LessonLine.LAB, Pattern.DOTALL | Pattern.MULTILINE);
            lecPattern = Pattern.compile(Patterns.LessonLine.LECTURE, Pattern.DOTALL | Pattern.MULTILINE);
            facPattern = Pattern.compile(Patterns.LessonLine.FACULTY, Pattern.DOTALL | Pattern.MULTILINE);
            teacherPattern = Pattern.compile(Patterns.LessonLine.TEACHER, Pattern.DOTALL | Pattern.MULTILINE);
            return this;
        }


        public Builder defaultOrderPatterns() {
            //ToDo move to UTIL class
            HashMap<Pattern, Integer> patternMap = new HashMap<>();
            try (FileInputStream fileInputStream = new FileInputStream("src/main/resources/parser-lessonorder.properties")) {
                Properties prop = new Properties();
                prop.load(fileInputStream);
                Enumeration<?> e = prop.propertyNames();
                while (e.hasMoreElements()) {
                    String key = (String) e.nextElement();
                    String value = prop.getProperty(key);
                    patternMap.put(Pattern.compile(value), Integer.valueOf(key));
                }
                fileInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            LessonLine.this.lessonOrderMap = patternMap;
            return this;
        }

    }

    public String getLine() {
        return line;
    }

    public DaySheet getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return "LessonLine{" +
                "line='" + line + '\'' +
                '}';
    }
}
