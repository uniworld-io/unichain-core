/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */

package org.unichain.common.utils;

import lombok.val;
import org.springframework.util.Assert;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;

public interface Utils {

  SecureRandom random = new SecureRandom();

  static SecureRandom getRandom() {
    return random;
  }

  static byte[] getBytes(char[] chars) {
    Charset cs = Charset.forName("UTF-8");
    CharBuffer cb = CharBuffer.allocate(chars.length);
    cb.put(chars);
    cb.flip();
    ByteBuffer bb = cs.encode(cb);

    return bb.array();
  }

  static String getIdShort(String Id) {
    return Id == null ? "<null>" : Id.substring(0, 8);
  }

  static char[] getChars(byte[] bytes) {
    Charset cs = Charset.forName("UTF-8");
    ByteBuffer bb = ByteBuffer.allocate(bytes.length);
    bb.put(bytes);
    bb.flip();
    CharBuffer cb = cs.decode(bb);

    return cb.array();
  }

  static byte[] clone(byte[] value) {
    byte[] clone = new byte[value.length];
    System.arraycopy(value, 0, clone, 0, value.length);
    return clone;
  }

  static String sizeToStr(long size) {
    if (size < 2 * (1L << 10)) {
      return size + "b";
    }
    if (size < 2 * (1L << 20)) {
      return String.format("%dKb", size / (1L << 10));
    }
    if (size < 2 * (1L << 30)) {
      return String.format("%dMb", size / (1L << 20));
    }
    return String.format("%dGb", size / (1L << 30));
  }

  static String formatDateLong(long time){
    Date date = new Date(time);
    String pattern = "yyyy-MM-dd";
    SimpleDateFormat format = new SimpleDateFormat(pattern);
    return format.format(date);
  }

  static String formatDateTimeLong(long time){
    Date date = new Date(time);
    String pattern = "yyyy-MM-dd HH:mm:ss";
    SimpleDateFormat format = new SimpleDateFormat(pattern);
    return format.format(date);
  }

  static String align(String s, char fillChar, int targetLen, boolean alignRight) {
    if (targetLen <= s.length()) {
      return s;
    }
    String alignString = repeat("" + fillChar, targetLen - s.length());
    return alignRight ? alignString + s : s + alignString;

  }

  static String repeat(String s, int n) {
    if (s.length() == 1) {
      byte[] bb = new byte[n];
      Arrays.fill(bb, s.getBytes()[0]);
      return new String(bb);
    } else {
      StringBuilder ret = new StringBuilder();
      for (int i = 0; i < n; i++) {
        ret.append(s);
      }
      return ret.toString();
    }
  }

  static <V> List<V> paging(List<V> all, int pageIndex, int pageSize){
    Assert.isTrue(pageIndex >=0 && pageSize > 0, "invalid paging info");
    if(all == null || all.isEmpty())
      return Collections.emptyList();
    int start = pageIndex * pageSize;
    int end = start + pageSize;
    if(start >= all.size())
       return new ArrayList<>();
    if(end >= all.size())
      end = all.size();
    return all.subList(start, end);
  }

  static BigInteger divideCeiling(BigInteger a, BigInteger b){
    val rets = a.divideAndRemainder(b);
    if(rets[1].compareTo(BigInteger.ZERO) > 0)
      return rets[0].add(BigInteger.ONE);
    else
      return rets[0];
  }
}
