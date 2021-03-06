package com.theopus.parser.obj;

import com.google.common.collect.Sets;
import com.theopus.entity.schedule.Circumstance;
import com.theopus.entity.schedule.Room;
import com.theopus.entity.schedule.enums.LessonOrder;
import com.theopus.parser.utl.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RoomDateBrackets {

    private static Logger log = LoggerFactory.getLogger(RoomDateBrackets.class);

    private String rightSplit;
    private LessonLine parent;
    private LessonOrder lessonOrder;


    protected RoomDateBrackets() {
    }

    public Set<Circumstance> parseCircumstacnes() {
        Set<Circumstance> circumstances = parseBrackets();
        circumstances.forEach(c -> c.setLessonOrder(lessonOrder));
        log.debug("{} = {}", circumstances);
        return circumstances;
    }


    Set<Circumstance> parseBrackets() {
        Room dummyRoom = new Room(Room.NO_AUDITORY);
        Set<Circumstance> result = new HashSet<>();
        Bracket bracket = splitToBrackets();
        HashSet<Circumstance> emptyRooms = new HashSet<>();
        HashSet<Circumstance> emptyDates = new HashSet<>();
        emptyRooms.clear();

        Room cacheRoom = dummyRoom;
        Set<LocalDate> cacheDates = new HashSet<>();

        for (Bracket current = bracket.parse(); current != null; current = current.next) {
            current.parse();
            Circumstance circumstance = new Circumstance();

            if (!current.dates.isEmpty() && !current.room.equals(dummyRoom)) {
                circumstance.setDates(current.dates);
                circumstance.setRoom(current.room);
                cacheDates = current.dates;
                cacheRoom = current.room;

                Room finalCacheRoom = cacheRoom;
                emptyRooms.forEach(c -> {
                    c.setRoom(finalCacheRoom);
                    result.add(c);
                });
                result.addAll(emptyRooms);
                emptyRooms.clear();
                Set<LocalDate> finalCacheDates = cacheDates;
                emptyDates.forEach(c -> {
                    c.setDates(finalCacheDates);
                    result.add(c);
                });
                result.addAll(emptyDates);
                emptyDates.clear();
                result.add(circumstance);
            }

            if (current.dates.isEmpty() && !current.room.equals(dummyRoom)) {
                circumstance.setRoom(current.room);
                cacheRoom = current.room;
                emptyDates.add(circumstance);
            }
            if (!current.dates.isEmpty() && current.room.equals(dummyRoom)) {
                circumstance.setDates(current.dates);
                cacheDates = current.dates;
                emptyRooms.add(circumstance);
            }

            if (current.next == null) {
                circumstance.setRoom(cacheRoom);
                circumstance.setDates(cacheDates);
                result.add(circumstance);

                if (current.dates.isEmpty() && cacheDates.isEmpty()) {
                    log.debug("No dates brackets in {} ,setting max range.{}", parent, fromToRange(
                            parent.getParent().getParent().getTable().getFromBound(parent.getParent().getDayOfWeek()),
                            parent.getParent().getParent().getTable().getToBound(parent.getParent().getDayOfWeek()),
                            false
                    ));
                    cacheDates.addAll(Sets.newHashSet(fromToRange(
                            parent.getParent().getParent().getTable().getFromBound(parent.getParent().getDayOfWeek()),
                            parent.getParent().getParent().getTable().getToBound(parent.getParent().getDayOfWeek()),
                            false
                    )));
                }

                Room finalCacheRoom = cacheRoom;
                emptyRooms.forEach(c -> {
                    c.setRoom(finalCacheRoom);
                    result.add(c);
                });
                emptyRooms.clear();
                Set<LocalDate> finalCacheDates = cacheDates;
                emptyDates.forEach(c -> {
                    c.setDates(finalCacheDates);
                    result.add(c);
                });
                emptyDates.clear();

            }
        }
        result.stream().filter(circumstance -> circumstance.getDates().isEmpty()).forEach(circumstance -> {
            log.error("Empty dates {} = {}, setting  max range.", parent, circumstance);
            circumstance.setDates(Sets.newHashSet(fromToRange(
                    parent.getParent().getParent().getTable().getFromBound(parent.getParent().getDayOfWeek()),
                    parent.getParent().getParent().getTable().getToBound(parent.getParent().getDayOfWeek()),
                    false
            )));
        });
        return result;
    }

    private Bracket splitToBrackets() {
        Matcher matcher = bracketsPattern.matcher(rightSplit);
        Bracket first = null;
        if (matcher.find()) {
            first = new Bracket(matcher.group(1));
            log.debug("Found bracket {}={}", rightSplit, matcher.group(1));
        } else {
            first = new Bracket("");
        }
        Bracket tmp = first;
        while (matcher.find()) {
            log.debug("Found bracket {}={}", rightSplit, matcher.group(1));
            tmp.next = new Bracket(matcher.group(1), null, tmp);
            tmp = tmp.next;
        }
        return first;
    }

    private Pattern bracketsPattern;
    private Pattern audPattern;
    private Pattern singleDatePattern;
    private Pattern fromToPatternW;
    private String fromDelimiter;
    private Pattern weekSkipPattern;
    private String toDelimiter;

    class Bracket {
        private Room room;
        private Set<LocalDate> dates;
        private String bracketContent;
        private Bracket next;
        private Bracket prev;


        public Bracket(String bracketContent) {
            this.bracketContent = bracketContent.trim();
        }

        public Bracket(String bracketContent, Bracket next, Bracket prev) {
            this.bracketContent = bracketContent.trim();
            this.next = next;
            this.prev = prev;
        }


        private Room parseRoom() {
            Matcher matcher2 = audPattern.matcher(bracketContent);
            if (matcher2.find()) {
                Room room = new Room();
                room.setName(matcher2.group());
                return room;
            } else {
                log.debug("No room avalible for line {} . Default has been setted. ", bracketContent);
                Room room = new Room();
                room.setName(Room.NO_AUDITORY);
                return room;
            }
        }

        private Set<LocalDate> parseDates() {
            Set<LocalDate> localDates = new HashSet<>();
            localDates.addAll(getSingleDates());
            localDates.addAll(getFromToDates());
            return localDates;

        }

        private Set<LocalDate> getSingleDates() {
            Set<LocalDate> localDates = new HashSet<>();
            Matcher matcher = singleDatePattern.matcher(bracketContent);
            while (matcher.find()) {

                localDates.add(convert(matcher.group(3)));
            }
            return localDates;
        }

        private Set<LocalDate> getFromToDates() {
            Set<LocalDate> localDates = new HashSet<>();
            Matcher matcher = fromToPatternW.matcher(bracketContent);
            Stack<Pair<String, Boolean>> fromStack = new Stack<>();
            Stack<Pair<String, Boolean>> toStack = new Stack<>();
            while (matcher.find()) {
                boolean weekSkip = false;
                if (!Objects.isNull(matcher.group(3))) {
                    weekSkip = true;
                }

                log.debug("From to dates {}={}={}", matcher.group(1), matcher.group(2), matcher.group(3));
                String group = matcher.group(1);
                if (group.equals(fromDelimiter)) {
                    fromStack.push(new Pair<>(matcher.group(2), weekSkip));
                } else if (group.equals(toDelimiter)) {
                    if (fromStack.empty()) {
                        toStack.add(new Pair<>(matcher.group(2), weekSkip));
                    } else {
                        Pair<String, Boolean> from = fromStack.pop();
                        String to = matcher.group(2);
                        localDates.addAll(fromToRange(
                                convert(from.getKey()), convert(to),
                                from.getValue() | weekSkip
                        ));
                    }
                }
                if (weekSkip) {
                    log.debug("Week skip detected at {} ", bracketContent);
                }
            }
            fromStack.forEach(pair -> localDates.addAll(fromToRange(
                    convert(pair.getKey()),
                    parent.getParent().getParent().getTable().getToBound(parent.getParent().getDayOfWeek()),
                    pair.getValue()
            )));
            toStack.forEach(pair -> localDates.addAll(fromToRange(
                    parent.getParent().getParent().getTable().getFromBound(parent.getParent().getDayOfWeek()),
                    convert(pair.getKey()),
                    pair.getValue()
            )));
            if (localDates.isEmpty()) {
                Matcher ws = weekSkipPattern.matcher(bracketContent);
                if (ws.find()) {
                    Set<LocalDate> fromToMax = fromToRange(
                            parent.getParent().getParent().getTable().getFromBound(parent.getParent().getDayOfWeek()),
                            parent.getParent().getParent().getTable().getToBound(parent.getParent().getDayOfWeek()),
                            true
                    );
                    log.warn("Week skip without date definition {},  setting max ", bracketContent, fromToMax);
                    localDates.addAll(fromToMax);
                }
            }
            return localDates;
        }

        private Bracket parse() {
            this.room = parseRoom();
            this.dates = parseDates();
            log.debug("Parsed bracket in {}={},{}", bracketContent, room, dates);
            return this;
        }
    }

    private LocalDate convert(String date) {
        return parent.getParent().getParent().convert(date);
    }

    private Set<LocalDate> fromToRange(LocalDate start, LocalDate end, boolean isWeekSkip) {
        int delta = isWeekSkip ? 7 * 2 : 7;
        int until = (int) start.until(end, ChronoUnit.DAYS);
        return Stream.iterate(start, d -> d.plusDays(delta))
                .limit(until / delta + 1)
                .collect(Collectors.toSet());
    }

    public RoomDateBrackets parent(LessonLine lessonLine) {
        this.parent = lessonLine;
        return this;
    }

    public RoomDateBrackets prepare(String rightSplit, LessonOrder lessonOrder) {
        this.rightSplit = rightSplit;
        this.lessonOrder = lessonOrder;
        return this;
    }

    public static RoomDateBrackets.Builder create() {
        return new RoomDateBrackets().new Builder();
    }

    public class Builder {

        public RoomDateBrackets build() {
            return RoomDateBrackets.this;
        }

        public Builder defaultPatterns() {
            bracketsPattern = Pattern.compile(Patterns.RoomDates.BRACKETS);
            audPattern = Pattern.compile(Patterns.RoomDates.AUDITORY);
            singleDatePattern = Pattern.compile(Patterns.RoomDates.SINGLE_DATE);
            fromToPatternW = Pattern.compile(Patterns.RoomDates.FROM_TO_DATE_W);
            fromDelimiter = Patterns.RoomDates.FROM_DELIMITER;
            toDelimiter = Patterns.RoomDates.TO_DELIMITER;
            weekSkipPattern = Pattern.compile(Patterns.RoomDates.WEAK_SKIP_PATTERN);
            return this;
        }
    }
}
