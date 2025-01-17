/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.query.ordering;

import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import org.apache.druid.common.guava.GuavaUtils;
import org.apache.druid.error.DruidException;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.math.BigDecimal;
import java.util.Comparator;

public class StringComparators
{
  public static final String LEXICOGRAPHIC_NAME = "lexicographic";
  public static final String ALPHANUMERIC_NAME = "alphanumeric";
  public static final String NUMERIC_NAME = "numeric";
  public static final String STRLEN_NAME = "strlen";
  public static final String VERSION_NAME = "version";
  public static final String NATURAL_NAME = "natural";

  public static final StringComparator LEXICOGRAPHIC = new LexicographicComparator();
  public static final StringComparator ALPHANUMERIC = new AlphanumericComparator();
  public static final StringComparator NUMERIC = new NumericComparator();
  public static final StringComparator STRLEN = new StrlenComparator();
  public static final StringComparator VERSION = new VersionComparator();
  public static final StringComparator NATURAL = new NaturalComparator();

  public static final int LEXICOGRAPHIC_CACHE_ID = 0x01;
  public static final int ALPHANUMERIC_CACHE_ID = 0x02;
  public static final int NUMERIC_CACHE_ID = 0x03;
  public static final int STRLEN_CACHE_ID = 0x04;
  public static final int VERSION_CACHE_ID = 0x05;
  public static final int NATURAL_CACHE_ID = 0x06;

  /**
   * Comparison using the natural comparator of {@link String}.
   *
   * Note that this is not equivalent to comparing UTF-8 byte arrays; see javadocs for
   * {@link StringUtils#compareUnicode(String, String)} and
   * {@link StringUtils#compareUtf8UsingJavaStringOrdering(byte[], byte[])}.
   */
  public static class LexicographicComparator extends StringComparator
  {
    private static final Ordering<String> ORDERING = Ordering.from(String::compareTo).nullsFirst();

    @Override
    public int compare(String s, String s2)
    {
      // Avoid comparisons for equal references
      // Assuming we mostly compare different strings, checking s.equals(s2) will only make the comparison slower.
      //noinspection StringEquality
      if (s == s2) {
        return 0;
      }

      return ORDERING.compare(s, s2);
    }

    @Override
    public boolean equals(Object o)
    {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      
      return true;
    }

    @Override
    public int hashCode()
    {
      return 0;
    }

    @Override
    public String toString()
    {
      return LEXICOGRAPHIC_NAME;
    }

    @Override
    public byte[] getCacheKey()
    {
      return new byte[]{(byte) LEXICOGRAPHIC_CACHE_ID};
    }
  }
  
  public static class AlphanumericComparator extends StringComparator
  {
    // This code is based on https://github.com/amjjd/java-alphanum, see
    // NOTICE file for more information
    @Override
    public int compare(String str1, String str2)
    {
      int[] pos = {0, 0};

      if (str1 == null) {
        if (str2 == null) {
          return 0;
        }
        return -1;
      } else if (str2 == null) {
        return 1;
      } else if (str1.length() == 0) {
        return str2.length() == 0 ? 0 : -1;
      } else if (str2.length() == 0) {
        return 1;
      }

      while (pos[0] < str1.length() && pos[1] < str2.length()) {
        int ch1 = str1.codePointAt(pos[0]);
        int ch2 = str2.codePointAt(pos[1]);

        int result;

        if (isDigit(ch1)) {
          result = isDigit(ch2) ? compareNumbers(str1, str2, pos) : -1;
        } else {
          result = isDigit(ch2) ? 1 : compareNonNumeric(str1, str2, pos);
        }

        if (result != 0) {
          return result;
        }
      }

      return Integer.compare(str1.length(), str2.length());
    }

    private int compareNumbers(String str0, String str1, int[] pos)
    {
      int delta = 0;
      int zeroes0 = 0, zeroes1 = 0;
      int ch0 = -1, ch1 = -1;

      // Skip leading zeroes, but keep a count of them.
      while (pos[0] < str0.length() && isZero(ch0 = str0.codePointAt(pos[0]))) {
        zeroes0++;
        pos[0] += Character.charCount(ch0);
      }
      while (pos[1] < str1.length() && isZero(ch1 = str1.codePointAt(pos[1]))) {
        zeroes1++;
        pos[1] += Character.charCount(ch1);
      }

      // If one sequence contains more significant digits than the
      // other, it's a larger number. In case they turn out to have
      // equal lengths, we compare digits at each position; the first
      // unequal pair determines which is the bigger number.
      while (true) {
        boolean noMoreDigits0 = (ch0 < 0) || !isDigit(ch0);
        boolean noMoreDigits1 = (ch1 < 0) || !isDigit(ch1);

        if (noMoreDigits0 && noMoreDigits1) {
          return delta != 0 ? delta : zeroes0 - zeroes1;
        } else if (noMoreDigits0) {
          return -1;
        } else if (noMoreDigits1) {
          return 1;
        } else if (delta == 0 && ch0 != ch1) {
          delta = valueOf(ch0) - valueOf(ch1);
        }

        if (pos[0] < str0.length()) {
          ch0 = str0.codePointAt(pos[0]);
          if (isDigit(ch0)) {
            pos[0] += Character.charCount(ch0);
          } else {
            ch0 = -1;
          }
        } else {
          ch0 = -1;
        }

        if (pos[1] < str1.length()) {
          ch1 = str1.codePointAt(pos[1]);
          if (isDigit(ch1)) {
            pos[1] += Character.charCount(ch1);
          } else {
            ch1 = -1;
          }
        } else {
          ch1 = -1;
        }
      }
    }

    private boolean isDigit(int ch)
    {
      return (ch >= '0' && ch <= '9') ||
          (ch >= '\u0660' && ch <= '\u0669') ||
          (ch >= '\u06F0' && ch <= '\u06F9') ||
          (ch >= '\u0966' && ch <= '\u096F') ||
          (ch >= '\uFF10' && ch <= '\uFF19');
    }

    private boolean isZero(int ch)
    {
      return ch == '0' || ch == '\u0660' || ch == '\u06F0' || ch == '\u0966' || ch == '\uFF10';
    }

    private int valueOf(int digit)
    {
      if (digit <= '9') {
        return digit - '0';
      }
      if (digit <= '\u0669') {
        return digit - '\u0660';
      }
      if (digit <= '\u06F9') {
        return digit - '\u06F0';
      }
      if (digit <= '\u096F') {
        return digit - '\u0966';
      }
      if (digit <= '\uFF19') {
        return digit - '\uFF10';
      }

      return digit;
    }

    private int compareNonNumeric(String str0, String str1, int[] pos)
    {
      // find the end of both non-numeric substrings
      int start0 = pos[0];
      int ch0 = str0.codePointAt(pos[0]);
      pos[0] += Character.charCount(ch0);
      while (pos[0] < str0.length() && !isDigit(ch0 = str0.codePointAt(pos[0]))) {
        pos[0] += Character.charCount(ch0);
      }

      int start1 = pos[1];
      int ch1 = str1.codePointAt(pos[1]);
      pos[1] += Character.charCount(ch1);
      while (pos[1] < str1.length() && !isDigit(ch1 = str1.codePointAt(pos[1]))) {
        pos[1] += Character.charCount(ch1);
      }

      // compare the substrings
      return String.CASE_INSENSITIVE_ORDER.compare(str0.substring(start0, pos[0]), str1.substring(start1, pos[1]));
    }

    @Override
    public boolean equals(Object o)
    {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode()
    {
      return 0;
    }

    @Override
    public String toString()
    {
      return ALPHANUMERIC_NAME;
    }

    @Override
    public byte[] getCacheKey()
    {
      return new byte[]{(byte) ALPHANUMERIC_CACHE_ID};
    }
  }

  public static class StrlenComparator extends StringComparator
  {
    private static final Ordering<String> ORDERING = Ordering.from(new Comparator<String>()
    {
      @Override
      public int compare(String s, String s2)
      {
        return Ints.compare(s.length(), s2.length());
      }
    }).nullsFirst().compound(Ordering.natural());

    @Override
    public int compare(String s, String s2)
    {
      // Optimization
      //noinspection StringEquality
      if (s == s2) {
        return 0;
      }
      
      return ORDERING.compare(s, s2);
    }

    @Override
    public boolean equals(Object o)
    {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode()
    {
      return 0;
    }

    @Override
    public String toString()
    {
      return STRLEN_NAME;
    }

    @Override
    public byte[] getCacheKey()
    {
      return new byte[]{(byte) STRLEN_CACHE_ID};
    }
  }

  private static BigDecimal convertStringToBigDecimal(String input)
  {
    if (input == null) {
      return null;
    }

    // treat unparseable Strings as nulls
    BigDecimal bd = null;
    try {
      bd = new BigDecimal(input);
    }
    catch (NumberFormatException ex) {
    }
    return bd;
  }

  public static class NumericComparator extends StringComparator
  {
    @Override
    public int compare(String o1, String o2)
    {
      // return if o1 and o2 are the same object
      // Assuming we mostly compare different strings, checking o1.equals(o2) will only make the comparison slower.
      //noinspection StringEquality
      if (o1 == o2) {
        return 0;
      }
      // we know o1 != o2
      if (o1 == null) {
        return -1;
      }
      if (o2 == null) {
        return 1;
      }

      // Creating a BigDecimal from a String is expensive (involves copying the String into a char[])
      // Converting the String to a Long first is faster.
      // We optimize here with the assumption that integer values are more common than floating point.
      Long long1 = GuavaUtils.tryParseLong(o1);
      Long long2 = GuavaUtils.tryParseLong(o2);

      if (long1 != null && long2 != null) {
        return Long.compare(long1, long2);
      }

      final BigDecimal bd1 = long1 == null ? convertStringToBigDecimal(o1) : new BigDecimal(long1);
      final BigDecimal bd2 = long2 == null ? convertStringToBigDecimal(o2) : new BigDecimal(long2);

      if (bd1 != null && bd2 != null) {
        return bd1.compareTo(bd2);
      }

      if (bd1 == null && bd2 == null) {
        // both Strings are unparseable, just compare lexicographically to have a well-defined ordering
        return LEXICOGRAPHIC.compare(o1, o2);
      }

      if (bd1 == null) {
        return -1;
      } else {
        return 1;
      }
    }

    @Override
    public String toString()
    {
      return NUMERIC_NAME;
    }

    @Override
    public boolean equals(Object o)
    {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode()
    {
      return 0;
    }

    @Override
    public byte[] getCacheKey()
    {
      return new byte[]{(byte) NUMERIC_CACHE_ID};
    }
  }

  public static class VersionComparator extends StringComparator
  {
    @Override
    public int compare(String o1, String o2)
    {
      //noinspection StringEquality
      if (o1 == o2) {
        return 0;
      }
      if (o1 == null) {
        return -1;
      }
      if (o2 == null) {
        return 1;
      }

      DefaultArtifactVersion version1 = new DefaultArtifactVersion(o1);
      DefaultArtifactVersion version2 = new DefaultArtifactVersion(o2);
      return version1.compareTo(version2);
    }

    @Override
    public String toString()
    {
      return VERSION_NAME;
    }

    @Override
    public boolean equals(Object o)
    {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode()
    {
      return 0;
    }

    @Override
    public byte[] getCacheKey()
    {
      return new byte[]{(byte) VERSION_CACHE_ID};
    }
  }

  /**
   * NaturalComparator refers to the natural ordering of the type that it refers.
   *
   * For example, if the type is Long, the natural ordering would be numeric
   * if the type is an array, the natural ordering would be lexicographic comparison of the natural ordering of the
   * elements in the arrays.
   *
   * It is a sigil value for the dimension that we can handle in the execution layer, and don't need the comparator for.
   * It is also a placeholder for dimensions that we don't have a comparator for (like arrays), but is a required for
   * planning
   */
  public static class NaturalComparator extends StringComparator
  {
    @Override
    public int compare(String o1, String o2)
    {
      throw DruidException.defensive("compare() should not be called for the NaturalComparator");
    }

    @Override
    public String toString()
    {
      return NATURAL_NAME;
    }

    @Override
    public boolean equals(Object o)
    {
      if (this == o) {
        return true;
      }
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode()
    {
      return 0;
    }

    @Override
    public byte[] getCacheKey()
    {
      return new byte[]{(byte) NATURAL_CACHE_ID};
    }
  }
}
