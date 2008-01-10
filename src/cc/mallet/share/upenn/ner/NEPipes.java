package cc.mallet.share.upenn.ner;



import java.io.*;

import java.util.*;
import java.util.regex.*;

import cc.mallet.pipe.*;
import cc.mallet.pipe.tsf.*;
import cc.mallet.share.weili.ner.enron.*;
import cc.mallet.types.*;

public class NEPipes extends SerialPipes {

    // *** General-purpose regex
    // Single-token:
    static String ALLCAPS = "([A-Z]*)";
    static String ALLLOWER = "([a-z]*)";
    static String INITCAPS = "([A-Z].*)";
    static String MIXEDCASE = "(.*[a-z].*[A-Z].*)";
    static String MIXEDNUM = "(.*[0-9].*)";

    static String ENDSENTENCE = "([.!?])";
    static String PUNCTUATION = "([:;,.!?-])";
    static String BRACKET = "([(){}\\[\\]])";
    static String ORDINAL = "([0-9]+(st|rd|th))";

    // Multiple-token:
    static String QUOTED = "([\"'].*[\"'])";
    static String BRACKETED = "([({\\[].*[)}\\]])";
    static String INITIAL = "([A-Z][.])";
    static String DOTS = "([.][.])";
    static String DASHES = "(--)";
    static String FRACTION = "(<DIGITS>/<DIGITS>)";

    // *** Regex for money
    static String DOTDECIMAL = "((<DIGITS>)?[.]<DIGITS>)";
    static String DECIMAL = "(<DIGITS>|"+DOTDECIMAL+")";

    static String NUMBER_WORD = "(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|million|billion|trillion)";
    static String CURRENCY = "(dollar(s)?|cent(s)?|pound(s)?|euro(s)?|franc(s)?|yen)"; // a sample
    static String MONEYWORDS = "("+NUMBER_WORD+"+"+CURRENCY+")";

    // big number, not Eur. notation
    static String COMMA_DECIMAL = "((<DIGITS>,)+<DIGITS>([.]<DIGITS>)?)";
    
    static String ILLION = "(m(illion)?|b(illion)?|MM|k)";
    static String MIXED_ILLION = "([0-9]+"+ILLION+")";
    static String RANGE = "("+DECIMAL+"-"+DECIMAL+")";

    // *** Regex for time
    static String TIMENUM = "(<DIGITS>:<DIGITS>(:<DIGITS>)?)";
    static String AMPM = "(am|a[.]m[.]|pm|p[.]m[.])";
    static String MIXED_AMPM = "([0-9]+"+AMPM+")";
    static String TIMEZONES = "(PST|PDT|MST|MDT|CST|CDT|EST|EDT|UTC|GMT)";
    static String SPECIALTIME = "(noon|midnight)";
    static String TIME = "(("+TIMENUM+AMPM+"?|(<DIGITS>)"+AMPM+"|"+
                           "(<DIGITS>:)?"+MIXED_AMPM+")"+
        TIMEZONES+"?|"+SPECIALTIME+")";
    static String TIMERANGE = "("+TIME+"(-|to|until)"+TIME+")";

    // *** Regex for phone #
    static String P10 = "(([(]?<DIGITS>[)]?[-]?)<DIGITS>[-]?<DIGITS>|"+
        "<DIGITS>[.]<DIGITS>[.]<DIGITS>)";
    static String P5 = "(<DIGITS>[-]<DIGITS>)";

    // *** Regex for dates
    static String DAY = "(<DIGITS>|[1-3]?[0-9](st|rd|th))";
    static String YEAR = "(<YEAR>)";
    static String DECADE = "(<YEARDECADE>)";
    static String MONTHNAME = "(January|February|March|April|May|June|July|August|"+
        "September|October|November|December)";
    static String MONTHABBR = "((Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|"+
        "Oct|Nov|Dec)[.]?)";
    static String MONTH = "("+MONTHNAME+"|"+MONTHABBR+")";
    static String WEEKDAYNAME = 
        "(Sunday|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday)";
    static String WEEKDAYABBR = "((Sun|Mon|Tue|Tues|Wed|Thu|Thur|Thurs|Fri|Sat)[.]?)";
    static String WEEKDAY = "("+WEEKDAYNAME+"|"+WEEKDAYABBR+")";

    static String MONTHDAY = "("+MONTH+DAY+")";
    static String DAYMONTHDAY = "("+WEEKDAY+"[,]?"+MONTHDAY+")";
    static String MONTHYEAR = "("+MONTH+"[,]?"+YEAR+")";
    static String MONTHDAYYEAR = "("+MONTHDAY+"[,]?"+YEAR+")";
    static String DAYMONTHDAYYEAR = "("+DAYMONTHDAY+"[,]?"+YEAR+")";

    static String SEP = "([-/])";
    static String SEPDATE = "(<DIGITS>"+SEP+"<DIGITS>("+SEP+"(<DIGITS>|"+YEAR+"))?)";
    static String FULLSEPDATE = "(<DIGITS>"+SEP+"<DIGITS>"+SEP+"(<DIGITS>|"+YEAR+"))";

    public NEPipes (File placeDir) {
        super(new Pipe[] {
            new TokenText("text="),

            new RegexMatches("SingleLetter", Pattern.compile("[A-Za-z]")),
            new RegexMatches("AllCaps", Pattern.compile(ALLCAPS)),
            new RegexMatches("AllLower", Pattern.compile(ALLLOWER)),
            new RegexMatches("InitCaps", Pattern.compile(INITCAPS)),
            new RegexMatches("MixedCase", Pattern.compile(MIXEDCASE)),
            new RegexMatches("MixedNum", Pattern.compile(MIXEDNUM)),
            new RegexMatches("EndSentPunc", Pattern.compile(ENDSENTENCE)),
            new RegexMatches("Punc", Pattern.compile(PUNCTUATION)),
            new RegexMatches("Bracket", Pattern.compile(BRACKET)),
            new RegexMatches("Ordinal", Pattern.compile
                             (ORDINAL, Pattern.CASE_INSENSITIVE)),


            new LongRegexMatches("Quoted", Pattern.compile(QUOTED), 3, 4),
            new LongRegexMatches("Bracketed", Pattern.compile(BRACKETED), 3, 4),
            new LongRegexMatches("Initial", Pattern.compile(INITIAL), 2, 2),
            new LongRegexMatches("Ellipse", Pattern.compile(DOTS), 2, 2),
            new LongRegexMatches("Dashes", Pattern.compile(DASHES), 2, 2),
            new LongRegexMatches("Fraction", Pattern.compile(FRACTION), 3, 3),
            new LongRegexMatches("DotDecimal", Pattern.compile(DOTDECIMAL), 2, 3),

            new LongRegexMatches("Percent", Pattern.compile
                                 ("("+RANGE+"|"+DECIMAL+")%"), 2, 4),
            new RegexMatches("10^3n", Pattern.compile
                             (ILLION, Pattern.CASE_INSENSITIVE)),
            new LongRegexMatches("Numeric", Pattern.compile(DECIMAL), 1, 3),
            new LongRegexMatches("BigNumber", Pattern.compile(COMMA_DECIMAL), 3, 7),
            new LongRegexMatches("kmbNumber", Pattern.compile
                                 (DECIMAL+ILLION, Pattern.CASE_INSENSITIVE), 1, 4),
            new RegexMatches("kmbMixed", Pattern.compile
                             (MIXED_ILLION, Pattern.CASE_INSENSITIVE)),
            new LongRegexMatches("Dollars", Pattern.compile
                                 ("[$]("+RANGE+"|"+DECIMAL+"|"+COMMA_DECIMAL+"|"+
                                  DECIMAL+ILLION+"|"+MIXED_ILLION+")",
                                  Pattern.CASE_INSENSITIVE), 2, 8),

            new RegexMatches("NumberWord", Pattern.compile
                             (NUMBER_WORD, Pattern.CASE_INSENSITIVE)),
            new RegexMatches("Currency", Pattern.compile
                             (CURRENCY, Pattern.CASE_INSENSITIVE)),
            new LongRegexMatches("MoneyWords", Pattern.compile
                                 (MONEYWORDS, Pattern.CASE_INSENSITIVE), 2, 4),

            new LongRegexMatches("AmPm", Pattern.compile
                                 (AMPM, Pattern.CASE_INSENSITIVE), 1, 4),
            new RegexMatches("MixedAmPm", Pattern.compile
                             (MIXED_AMPM, Pattern.CASE_INSENSITIVE)),
            new LongRegexMatches("TimeNum", Pattern.compile(TIMENUM), 3, 5),
            new RegexMatches("TimeZone", Pattern.compile
                             (TIMEZONES, Pattern.CASE_INSENSITIVE)),
            new LongRegexMatches("Time", Pattern.compile
                                 (TIME, Pattern.CASE_INSENSITIVE), 1, 9),
            new LongRegexMatches("TimeRange", Pattern.compile
                                 (TIMERANGE, Pattern.CASE_INSENSITIVE), 3, 19),

            new LongRegexMatches("P10", Pattern.compile(P10), 3, 7),
            new LongRegexMatches("P5", Pattern.compile(P10), 3, 3),
            new LongRegexMatches("Phone", Pattern.compile(P10+"|"+P5), 3, 7),

            new RegexMatches("UncasedMonthName", Pattern.compile
                             (MONTHNAME, Pattern.CASE_INSENSITIVE)),
            new LongRegexMatches("UncasedMonthAbbr", Pattern.compile
                                 (MONTHABBR, Pattern.CASE_INSENSITIVE), 1, 2),
            new LongRegexMatches("CasedMonth", Pattern.compile(MONTH), 1, 2),
            new LongRegexMatches("UncasedMonth", Pattern.compile
                                 (MONTH, Pattern.CASE_INSENSITIVE), 1, 2),
            
            new RegexMatches("UncasedWeekdayName", Pattern.compile
                             (WEEKDAYNAME, Pattern.CASE_INSENSITIVE)),
            new LongRegexMatches("UncasedWeekdayAbbr", Pattern.compile
                                 (WEEKDAYABBR, Pattern.CASE_INSENSITIVE), 1, 2),
            new LongRegexMatches("CasedWeekday", Pattern.compile(WEEKDAY), 1, 2),
            new LongRegexMatches("UncasedWeekday", Pattern.compile
                                 (WEEKDAY, Pattern.CASE_INSENSITIVE), 1, 2),

            new LongRegexMatches("MonthDay", Pattern.compile
                                 (MONTHDAY, Pattern.CASE_INSENSITIVE), 2, 3),
            new LongRegexMatches("DayMonthDay", Pattern.compile
                                 (DAYMONTHDAY, Pattern.CASE_INSENSITIVE), 3, 6),
            new LongRegexMatches("MonthYear", Pattern.compile
                                 (MONTHYEAR, Pattern.CASE_INSENSITIVE), 2, 4),
            new LongRegexMatches("MonthDayYear", Pattern.compile
                                 (MONTHDAYYEAR, Pattern.CASE_INSENSITIVE), 3, 5),
            new LongRegexMatches("DayMonthDayYear", Pattern.compile
                                 (DAYMONTHDAYYEAR, Pattern.CASE_INSENSITIVE), 4, 8),

            new LongRegexMatches("SeparatorDate", Pattern.compile(SEPDATE), 3, 5),
            new LongRegexMatches("FullSeparatorDate", Pattern.compile(FULLSEPDATE), 5, 5),

            new ListMember("Country", new File(placeDir, "countries.txt"), false),
            new ListMember("Africa", new File(placeDir, "africa.txt"), true),
            new ListMember("Asia", new File(placeDir, "asia.txt"), true),
            new ListMember("Europe", new File(placeDir, "europe.txt"), true),
            new ListMember("NorAm", new File(placeDir, "north_america.txt"), true),
            new ListMember("SouAm", new File(placeDir, "south_america.txt"), true),
            new ListMember("Island", new File(placeDir, "islands.txt"), true),
            new ListMember("Region", new File(placeDir, "regions.txt"), true),
            new ListMember("USState", new File(placeDir, "states.txt"), true),
            new ListMember("CanadaProv", new File(placeDir, "provinces.txt"), true),
            new ListMember("City", new File(placeDir, "cities.txt"), true),
            new ListMember("USCity", new File(placeDir, "us_cities.txt"), true),
            new ListMember("Terrain", new File(placeDir, "terrain.txt"), true),
            new ListMember("Geographical", new File(placeDir, "geo.txt"), true),

            new LengthBins("Length", new int[] {1,2,3,5,10}),

            new FeatureWindow(1, 1), // should be the last feature pipe
        });
    }

}
