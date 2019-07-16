/*
 * Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept. This file is part of
 * "MALLET" (MAchine Learning for LanguagE Toolkit). http://www.cs.umass.edu/~mccallum/mallet This
 * software is provided under the terms of the Common Public License, version 1.0, as published by
 * http://www.opensource.org. For further information, see the file `LICENSE' included with this
 * distribution.
 */
package cc.mallet.pipe;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.mallet.types.Instance;

/**
 * Be careful here: this pipe must be applied before {@link CharSequenceLowercase} because it is
 * case sensitive.
 */
public class CharSequenceReplaceHtmlEntities extends Pipe implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final int CURRENT_SERIAL_VERSION = 0;
  private static final String HTML_ENTITY_DIGIT = "&#\\d+;";
  private static final String HTML_ENTITY_ALPHA = "&\\w+;";
  private static final String HTML_AMP = "&amp;";
  private static final Pattern PATTERN_HTML_ENTITY_DIGIT = Pattern.compile(HTML_ENTITY_DIGIT);
  private static final Pattern PATTERN_HTML_ENTITY_ALPHA = Pattern.compile(HTML_ENTITY_ALPHA);
  private static final Map<String, Integer> NAME_2_CODE_POINT = new HashMap<String, Integer>();

  static {

    // All HTML4 entities as defined here: http://www.w3.org/TR/html4/sgml/entities.html
    // Added: amp, lt, gt, quot and apos
    NAME_2_CODE_POINT.put("quot", 34);
    NAME_2_CODE_POINT.put("amp", 38);
    NAME_2_CODE_POINT.put("apos", 39);
    NAME_2_CODE_POINT.put("lt", 60);
    NAME_2_CODE_POINT.put("gt", 62);
    NAME_2_CODE_POINT.put("nbsp", 160);
    NAME_2_CODE_POINT.put("iexcl", 161);
    NAME_2_CODE_POINT.put("cent", 162);
    NAME_2_CODE_POINT.put("pound", 163);
    NAME_2_CODE_POINT.put("curren", 164);
    NAME_2_CODE_POINT.put("yen", 165);
    NAME_2_CODE_POINT.put("brvbar", 166);
    NAME_2_CODE_POINT.put("sect", 167);
    NAME_2_CODE_POINT.put("uml", 168);
    NAME_2_CODE_POINT.put("copy", 169);
    NAME_2_CODE_POINT.put("ordf", 170);
    NAME_2_CODE_POINT.put("laquo", 171);
    NAME_2_CODE_POINT.put("not", 172);
    NAME_2_CODE_POINT.put("shy", 173);
    NAME_2_CODE_POINT.put("reg", 174);
    NAME_2_CODE_POINT.put("macr", 175);
    NAME_2_CODE_POINT.put("deg", 176);
    NAME_2_CODE_POINT.put("plusmn", 177);
    NAME_2_CODE_POINT.put("sup2", 178);
    NAME_2_CODE_POINT.put("sup3", 179);
    NAME_2_CODE_POINT.put("acute", 180);
    NAME_2_CODE_POINT.put("micro", 181);
    NAME_2_CODE_POINT.put("para", 182);
    NAME_2_CODE_POINT.put("middot", 183);
    NAME_2_CODE_POINT.put("cedil", 184);
    NAME_2_CODE_POINT.put("sup1", 185);
    NAME_2_CODE_POINT.put("ordm", 186);
    NAME_2_CODE_POINT.put("raquo", 187);
    NAME_2_CODE_POINT.put("frac14", 188);
    NAME_2_CODE_POINT.put("frac12", 189);
    NAME_2_CODE_POINT.put("frac34", 190);
    NAME_2_CODE_POINT.put("iquest", 191);
    NAME_2_CODE_POINT.put("Agrave", 192);
    NAME_2_CODE_POINT.put("Aacute", 193);
    NAME_2_CODE_POINT.put("Acirc", 194);
    NAME_2_CODE_POINT.put("Atilde", 195);
    NAME_2_CODE_POINT.put("Auml", 196);
    NAME_2_CODE_POINT.put("Aring", 197);
    NAME_2_CODE_POINT.put("AElig", 198);
    NAME_2_CODE_POINT.put("Ccedil", 199);
    NAME_2_CODE_POINT.put("Egrave", 200);
    NAME_2_CODE_POINT.put("Eacute", 201);
    NAME_2_CODE_POINT.put("Ecirc", 202);
    NAME_2_CODE_POINT.put("Euml", 203);
    NAME_2_CODE_POINT.put("Igrave", 204);
    NAME_2_CODE_POINT.put("Iacute", 205);
    NAME_2_CODE_POINT.put("Icirc", 206);
    NAME_2_CODE_POINT.put("Iuml", 207);
    NAME_2_CODE_POINT.put("ETH", 208);
    NAME_2_CODE_POINT.put("Ntilde", 209);
    NAME_2_CODE_POINT.put("Ograve", 210);
    NAME_2_CODE_POINT.put("Oacute", 211);
    NAME_2_CODE_POINT.put("Ocirc", 212);
    NAME_2_CODE_POINT.put("Otilde", 213);
    NAME_2_CODE_POINT.put("Ouml", 214);
    NAME_2_CODE_POINT.put("times", 215);
    NAME_2_CODE_POINT.put("Oslash", 216);
    NAME_2_CODE_POINT.put("Ugrave", 217);
    NAME_2_CODE_POINT.put("Uacute", 218);
    NAME_2_CODE_POINT.put("Ucirc", 219);
    NAME_2_CODE_POINT.put("Uuml", 220);
    NAME_2_CODE_POINT.put("Yacute", 221);
    NAME_2_CODE_POINT.put("THORN", 222);
    NAME_2_CODE_POINT.put("szlig", 223);
    NAME_2_CODE_POINT.put("agrave", 224);
    NAME_2_CODE_POINT.put("aacute", 225);
    NAME_2_CODE_POINT.put("acirc", 226);
    NAME_2_CODE_POINT.put("atilde", 227);
    NAME_2_CODE_POINT.put("auml", 228);
    NAME_2_CODE_POINT.put("aring", 229);
    NAME_2_CODE_POINT.put("aelig", 230);
    NAME_2_CODE_POINT.put("ccedil", 231);
    NAME_2_CODE_POINT.put("egrave", 232);
    NAME_2_CODE_POINT.put("eacute", 233);
    NAME_2_CODE_POINT.put("ecirc", 234);
    NAME_2_CODE_POINT.put("euml", 235);
    NAME_2_CODE_POINT.put("igrave", 236);
    NAME_2_CODE_POINT.put("iacute", 237);
    NAME_2_CODE_POINT.put("icirc", 238);
    NAME_2_CODE_POINT.put("iuml", 239);
    NAME_2_CODE_POINT.put("eth", 240);
    NAME_2_CODE_POINT.put("ntilde", 241);
    NAME_2_CODE_POINT.put("ograve", 242);
    NAME_2_CODE_POINT.put("oacute", 243);
    NAME_2_CODE_POINT.put("ocirc", 244);
    NAME_2_CODE_POINT.put("otilde", 245);
    NAME_2_CODE_POINT.put("ouml", 246);
    NAME_2_CODE_POINT.put("divide", 247);
    NAME_2_CODE_POINT.put("oslash", 248);
    NAME_2_CODE_POINT.put("ugrave", 249);
    NAME_2_CODE_POINT.put("uacute", 250);
    NAME_2_CODE_POINT.put("ucirc", 251);
    NAME_2_CODE_POINT.put("uuml", 252);
    NAME_2_CODE_POINT.put("yacute", 253);
    NAME_2_CODE_POINT.put("thorn", 254);
    NAME_2_CODE_POINT.put("yuml", 255);
    NAME_2_CODE_POINT.put("fnof", 402);
    NAME_2_CODE_POINT.put("Alpha", 913);
    NAME_2_CODE_POINT.put("Beta", 914);
    NAME_2_CODE_POINT.put("Gamma", 915);
    NAME_2_CODE_POINT.put("Delta", 916);
    NAME_2_CODE_POINT.put("Epsilon", 917);
    NAME_2_CODE_POINT.put("Zeta", 918);
    NAME_2_CODE_POINT.put("Eta", 919);
    NAME_2_CODE_POINT.put("Theta", 920);
    NAME_2_CODE_POINT.put("Iota", 921);
    NAME_2_CODE_POINT.put("Kappa", 922);
    NAME_2_CODE_POINT.put("Lambda", 923);
    NAME_2_CODE_POINT.put("Mu", 924);
    NAME_2_CODE_POINT.put("Nu", 925);
    NAME_2_CODE_POINT.put("Xi", 926);
    NAME_2_CODE_POINT.put("Omicron", 927);
    NAME_2_CODE_POINT.put("Pi", 928);
    NAME_2_CODE_POINT.put("Rho", 929);
    NAME_2_CODE_POINT.put("Sigma", 931);
    NAME_2_CODE_POINT.put("Tau", 932);
    NAME_2_CODE_POINT.put("Upsilon", 933);
    NAME_2_CODE_POINT.put("Phi", 934);
    NAME_2_CODE_POINT.put("Chi", 935);
    NAME_2_CODE_POINT.put("Psi", 936);
    NAME_2_CODE_POINT.put("Omega", 937);
    NAME_2_CODE_POINT.put("alpha", 945);
    NAME_2_CODE_POINT.put("beta", 946);
    NAME_2_CODE_POINT.put("gamma", 947);
    NAME_2_CODE_POINT.put("delta", 948);
    NAME_2_CODE_POINT.put("epsilon", 949);
    NAME_2_CODE_POINT.put("zeta", 950);
    NAME_2_CODE_POINT.put("eta", 951);
    NAME_2_CODE_POINT.put("theta", 952);
    NAME_2_CODE_POINT.put("iota", 953);
    NAME_2_CODE_POINT.put("kappa", 954);
    NAME_2_CODE_POINT.put("lambda", 955);
    NAME_2_CODE_POINT.put("mu", 956);
    NAME_2_CODE_POINT.put("nu", 957);
    NAME_2_CODE_POINT.put("xi", 958);
    NAME_2_CODE_POINT.put("omicron", 959);
    NAME_2_CODE_POINT.put("pi", 960);
    NAME_2_CODE_POINT.put("rho", 961);
    NAME_2_CODE_POINT.put("sigmaf", 962);
    NAME_2_CODE_POINT.put("sigma", 963);
    NAME_2_CODE_POINT.put("tau", 964);
    NAME_2_CODE_POINT.put("upsilon", 965);
    NAME_2_CODE_POINT.put("phi", 966);
    NAME_2_CODE_POINT.put("chi", 967);
    NAME_2_CODE_POINT.put("psi", 968);
    NAME_2_CODE_POINT.put("omega", 969);
    NAME_2_CODE_POINT.put("thetasym", 977);
    NAME_2_CODE_POINT.put("upsih", 978);
    NAME_2_CODE_POINT.put("piv", 982);
    NAME_2_CODE_POINT.put("bull", 8226);
    NAME_2_CODE_POINT.put("hellip", 8230);
    NAME_2_CODE_POINT.put("prime", 8242);
    NAME_2_CODE_POINT.put("Prime", 8243);
    NAME_2_CODE_POINT.put("oline", 8254);
    NAME_2_CODE_POINT.put("frasl", 8260);
    NAME_2_CODE_POINT.put("weierp", 8472);
    NAME_2_CODE_POINT.put("image", 8465);
    NAME_2_CODE_POINT.put("real", 8476);
    NAME_2_CODE_POINT.put("trade", 8482);
    NAME_2_CODE_POINT.put("alefsym", 8501);
    NAME_2_CODE_POINT.put("larr", 8592);
    NAME_2_CODE_POINT.put("uarr", 8593);
    NAME_2_CODE_POINT.put("rarr", 8594);
    NAME_2_CODE_POINT.put("darr", 8595);
    NAME_2_CODE_POINT.put("harr", 8596);
    NAME_2_CODE_POINT.put("crarr", 8629);
    NAME_2_CODE_POINT.put("lArr", 8656);
    NAME_2_CODE_POINT.put("uArr", 8657);
    NAME_2_CODE_POINT.put("rArr", 8658);
    NAME_2_CODE_POINT.put("dArr", 8659);
    NAME_2_CODE_POINT.put("hArr", 8660);
    NAME_2_CODE_POINT.put("forall", 8704);
    NAME_2_CODE_POINT.put("part", 8706);
    NAME_2_CODE_POINT.put("exist", 8707);
    NAME_2_CODE_POINT.put("empty", 8709);
    NAME_2_CODE_POINT.put("nabla", 8711);
    NAME_2_CODE_POINT.put("isin", 8712);
    NAME_2_CODE_POINT.put("notin", 8713);
    NAME_2_CODE_POINT.put("ni", 8715);
    NAME_2_CODE_POINT.put("prod", 8719);
    NAME_2_CODE_POINT.put("sum", 8721);
    NAME_2_CODE_POINT.put("minus", 8722);
    NAME_2_CODE_POINT.put("lowast", 8727);
    NAME_2_CODE_POINT.put("radic", 8730);
    NAME_2_CODE_POINT.put("prop", 8733);
    NAME_2_CODE_POINT.put("infin", 8734);
    NAME_2_CODE_POINT.put("ang", 8736);
    NAME_2_CODE_POINT.put("and", 8743);
    NAME_2_CODE_POINT.put("or", 8744);
    NAME_2_CODE_POINT.put("cap", 8745);
    NAME_2_CODE_POINT.put("cup", 8746);
    NAME_2_CODE_POINT.put("int", 8747);
    NAME_2_CODE_POINT.put("there4", 8756);
    NAME_2_CODE_POINT.put("sim", 8764);
    NAME_2_CODE_POINT.put("cong", 8773);
    NAME_2_CODE_POINT.put("asymp", 8776);
    NAME_2_CODE_POINT.put("ne", 8800);
    NAME_2_CODE_POINT.put("equiv", 8801);
    NAME_2_CODE_POINT.put("le", 8804);
    NAME_2_CODE_POINT.put("ge", 8805);
    NAME_2_CODE_POINT.put("sub", 8834);
    NAME_2_CODE_POINT.put("sup", 8835);
    NAME_2_CODE_POINT.put("nsub", 8836);
    NAME_2_CODE_POINT.put("sube", 8838);
    NAME_2_CODE_POINT.put("supe", 8839);
    NAME_2_CODE_POINT.put("oplus", 8853);
    NAME_2_CODE_POINT.put("otimes", 8855);
    NAME_2_CODE_POINT.put("perp", 8869);
    NAME_2_CODE_POINT.put("sdot", 8901);
    NAME_2_CODE_POINT.put("lceil", 8968);
    NAME_2_CODE_POINT.put("rceil", 8969);
    NAME_2_CODE_POINT.put("lfloor", 8970);
    NAME_2_CODE_POINT.put("rfloor", 8971);
    NAME_2_CODE_POINT.put("lang", 9001);
    NAME_2_CODE_POINT.put("rang", 9002);
    NAME_2_CODE_POINT.put("loz", 9674);
    NAME_2_CODE_POINT.put("spades", 9824);
    NAME_2_CODE_POINT.put("clubs", 9827);
    NAME_2_CODE_POINT.put("hearts", 9829);
    NAME_2_CODE_POINT.put("diams", 9830);
    NAME_2_CODE_POINT.put("OElig", 338);
    NAME_2_CODE_POINT.put("oelig", 339);
    NAME_2_CODE_POINT.put("Scaron", 352);
    NAME_2_CODE_POINT.put("scaron", 353);
    NAME_2_CODE_POINT.put("Yuml", 376);
    NAME_2_CODE_POINT.put("circ", 710);
    NAME_2_CODE_POINT.put("tilde", 732);
    NAME_2_CODE_POINT.put("ensp", 8194);
    NAME_2_CODE_POINT.put("emsp", 8195);
    NAME_2_CODE_POINT.put("thinsp", 8201);
    NAME_2_CODE_POINT.put("g", 8204);
    NAME_2_CODE_POINT.put("zwj", 8205);
    NAME_2_CODE_POINT.put("lrm", 8206);
    NAME_2_CODE_POINT.put("rlm", 8207);
    NAME_2_CODE_POINT.put("ndash", 8211);
    NAME_2_CODE_POINT.put("mdash", 8212);
    NAME_2_CODE_POINT.put("lsquo", 8216);
    NAME_2_CODE_POINT.put("rsquo", 8217);
    NAME_2_CODE_POINT.put("sbquo", 8218);
    NAME_2_CODE_POINT.put("ldquo", 8220);
    NAME_2_CODE_POINT.put("rdquo", 8221);
    NAME_2_CODE_POINT.put("bdquo", 8222);
    NAME_2_CODE_POINT.put("dagger", 8224);
    NAME_2_CODE_POINT.put("Dagger", 8225);
    NAME_2_CODE_POINT.put("permil", 8240);
    NAME_2_CODE_POINT.put("lsaquo", 8249);
    NAME_2_CODE_POINT.put("rsaquo", 8250);
    NAME_2_CODE_POINT.put("euro", 8364);
  }

  public CharSequenceReplaceHtmlEntities() {}

  @Override
  public Instance pipe(Instance carrier) {
    if (carrier.getData() instanceof String) {
      String data = (String) carrier.getData();
      carrier.setData(replace(data));
      return carrier;
    } else {
      throw new IllegalArgumentException(
          "CharSequenceReplaceHtmlEntities expects a String, found a "
              + carrier.getData().getClass());
    }
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeInt(CURRENT_SERIAL_VERSION);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    int version = in.readInt();
  }

  /**
   * Replace all the HTML entities in string with their corresponding unicode characters.
   */
  private String replace(String string) {

    if (string == null) {
      return null;
    }

    string = string.replace(HTML_AMP, "&");
    Set<String> digits = new HashSet<>();

    Matcher digitsMatcher = PATTERN_HTML_ENTITY_DIGIT.matcher(string);
    while (digitsMatcher.find()) {
      digits.add(digitsMatcher.group());
    }
    if (digits.size() > 0) {
      for (String digit : digits) {
        int codePoint = Integer.valueOf(digit.substring(2, digit.length() - 1));
        string = string.replace(digit, Character.toString((char) codePoint));
      }
    }

    Set<String> alphas = new HashSet<>();
    Matcher alphasMatcher = PATTERN_HTML_ENTITY_ALPHA.matcher(string);
    while (alphasMatcher.find()) {
      alphas.add(alphasMatcher.group());
    }
    if (alphas.size() > 0) {
      for (String alpha : alphas) {
        String name = alpha.substring(1, alpha.length() - 1);
        string = string.replace(alpha,
            Character.toString((char) NAME_2_CODE_POINT.get(name).intValue()));
      }
    }
    return string;
  }
}
