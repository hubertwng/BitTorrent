import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import lombok.Data;

@Data
public class Bencode {

  private static final Gson gson = new Gson();

  private BencodeType type;

  private Object value;

  public String getString() {
    if (!BencodeType.STRING.equals(this.type)) {
      throw new RuntimeException("cant get string value because current type is " + type);
    }
    return value.toString();
  }

  public String encodeToString() {
    StringBuilder builder = new StringBuilder();
    switch (type) {
      case LIST:
        builder.append("l");
        for (Object object : (List) value) {
          Bencode bencode = (Bencode) object;
          builder.append(bencode.encodeToString());
        }
        return builder.append("e").toString();
      case DIC:
        builder.append("d");
        for (Map.Entry entry : ((Map<String, Bencode>) value).entrySet()) {
          String key = (String) entry.getKey();
          builder.append(key.length()).append(":").append(key);
          Bencode bencode = (Bencode) entry.getValue();
          builder.append(bencode.encodeToString());
        }
        return builder.append("e").toString();
      case STRING:
        String str = (String) value;
        return builder.append(str.length()).append(":").append(str).toString();
      case INT:
        return builder.append("i").append((Long) value).append("e").toString();
      default:
        throw new RuntimeException("illegal bencode type:" + type);
    }
  }

  public String toSimpleJsonString() {
    switch (type) {
      case STRING:
        return value.toString();
      case INT:
        return ((Long) value).toString();
      case LIST:
        List<String> ans = new ArrayList<>();
        for (Object object : (List) value) {
          Bencode bencode = (Bencode) object;
          ans.add(bencode.toSimpleJsonString());
        }
        return gson.toJson(ans);
      case DIC:
        Map<String, Object> jsonMap = new HashMap<>();
        for (Map.Entry entry : ((Map<String, Bencode>) value).entrySet()) {
          String key = (String) entry.getKey();
          String value = ((Bencode) entry.getValue()).toSimpleJsonString();
          jsonMap.put(key, value);
        }
        return gson.toJson(jsonMap);
      default:
        throw new RuntimeException("illegal bencode type:" + type);
    }
  }


  public static Bencode fromString(String string) {
    return fromBytes(string.getBytes(StandardCharsets.UTF_8));
  }

  public static Bencode fromBytes(byte[] bytes) {
    if (bytes == null) {
      throw new RuntimeException("illegal bencode string");
    }

    BencodeType bencodeType = BencodeType.fromByte(bytes[0]);
    switch (bencodeType) {
      case LIST:
        return decodeToList(bytes);
      case DIC:
        return decodeToDic(bytes);
      case STRING:
        return decodeToString(bytes);
      case INT:
        return decodeToInt(bytes);
      default:
        throw new RuntimeException("illegal bencode type:" + bencodeType);
    }

  }

  private static Bencode decodeToList(byte[] bytes) {
    Bencode bencode = new Bencode();
    bencode.setType(BencodeType.LIST);

    if (bytes == null) {
      bencode.setValue(new ArrayList<>());
      return bencode;
    }
    parseNextList(bytes, 1, bencode);

    return bencode;
  }

  private static Bencode decodeToString(byte[] bytes) {
    Bencode bencode = new Bencode();
    bencode.setType(BencodeType.STRING);
    parseNextString(bytes, 1, bencode);
    return bencode;

  }

  private static Bencode decodeToInt(byte[] bytes) {
    Bencode bencode = new Bencode();
    bencode.setType(BencodeType.INT);
    parseNextInt(bytes, 1, bencode);
    return bencode;
  }

  private static Bencode decodeToDic(byte[] bytes) {
    Bencode bencode = new Bencode();
    bencode.setType(BencodeType.DIC);
    parseNextDic(bytes, 1, bencode);

    return bencode;
  }


  private static int parseNextString(byte[] bytes, int parsingCodeIndex, Bencode bencode) {
    if (!Character.isDigit(bytes[parsingCodeIndex])) {
      throw new RuntimeException("illegal string type bencode");
    }
    int lengthStartCodeIndex = parsingCodeIndex;
    int lengthEndCodeIndex = lengthStartCodeIndex + 1;
    while (':' != bytes[lengthEndCodeIndex] && lengthEndCodeIndex < bytes.length) {
      lengthEndCodeIndex++;
    }
    byte[] lengthBytes = new byte[lengthEndCodeIndex - lengthStartCodeIndex];
    System.arraycopy(bytes, lengthStartCodeIndex, lengthBytes, 0, lengthEndCodeIndex - lengthStartCodeIndex);
    int length = Integer.parseInt(new String(lengthBytes, StandardCharsets.UTF_8));
    int stringStartCodeIndex = lengthEndCodeIndex + 1;
    byte[] valueBytes = new byte[length];
    System.arraycopy(bytes, stringStartCodeIndex, valueBytes, 0, length);
    bencode.setValue(new String(valueBytes, StandardCharsets.UTF_8));
    return stringStartCodeIndex + length;
  }

  private static int parseNextInt(byte[] bytes, int parsingCodeIndex, Bencode bencode) {
    if ('i' != bytes[parsingCodeIndex]) {
      throw new RuntimeException("illegal int type bencode");
    }
    int intStartCodeIndex = parsingCodeIndex + 1;
    int intEndCodeIndex = intStartCodeIndex + 1;
    while ('e' != bytes[intEndCodeIndex] && intEndCodeIndex < bytes.length) {
      intEndCodeIndex++;
    }
    byte[] numBytes = new byte[intEndCodeIndex - intStartCodeIndex];
    System.arraycopy(bytes, intStartCodeIndex, numBytes, 0, intEndCodeIndex - intStartCodeIndex);
    bencode.setValue(Long.parseLong(new String(numBytes, StandardCharsets.UTF_8)));
    return intEndCodeIndex + 1;
  }

  private static int parseNextList(byte[] bytes, int parsingCodeIndex, Bencode bencode) {
    List<Bencode> bencodeList = new ArrayList<>();
    bencode.setValue(bencodeList);
    while ('e' != bytes[parsingCodeIndex] && parsingCodeIndex < bytes.length) {
      BencodeType type = BencodeType.fromByte(bytes[parsingCodeIndex]);
      Bencode nextBencode = new Bencode();
      nextBencode.setType(type);
      switch (type) {
        case INT:
          parsingCodeIndex = parseNextInt(bytes, parsingCodeIndex, nextBencode);
          break;
        case STRING:
          parsingCodeIndex = parseNextString(bytes, parsingCodeIndex, nextBencode);
          break;
        case LIST:
          parsingCodeIndex = parseNextList(bytes, parsingCodeIndex + 1, nextBencode);
          break;
        case DIC:
          parsingCodeIndex = parseNextDic(bytes, parsingCodeIndex + 1, nextBencode);
          break;
        default:
          throw new RuntimeException("illegal type in list bencode type");

      }
      bencodeList.add(nextBencode);
    }
    return parsingCodeIndex + 1;
  }

  private static int parseNextDic(byte[] bytes, int parsingCodeIndex, Bencode dicBencode) {
    Map<String, Bencode> dic = new HashMap<>();
    dicBencode.setValue(dic);
    while ('e' != bytes[parsingCodeIndex] && parsingCodeIndex < bytes.length) {
      BencodeType keyType = BencodeType.fromByte(bytes[parsingCodeIndex]);
      if (!BencodeType.STRING.equals(keyType)) {
        throw new RuntimeException("collection dic bencode's key must only string but is " + keyType);
      }
      Bencode key = new Bencode();
      key.setType(keyType);
      parsingCodeIndex = parseNextString(bytes, parsingCodeIndex, key);

      if (parsingCodeIndex >= bytes.length) {
        throw new RuntimeException("collection dic bencode's must have a value");
      }
      BencodeType valueType = BencodeType.fromByte(bytes[parsingCodeIndex]);
      Bencode valueBencode = new Bencode();
      valueBencode.setType(valueType);
      switch (valueType) {
        case STRING:
          parsingCodeIndex = parseNextString(bytes, parsingCodeIndex, valueBencode);
          break;
        case INT:
          parsingCodeIndex = parseNextInt(bytes, parsingCodeIndex, valueBencode);
          break;
        case LIST:
          parsingCodeIndex = parseNextList(bytes, parsingCodeIndex + 1, valueBencode);
          break;
        case DIC:
          parsingCodeIndex = parseNextDic(bytes, parsingCodeIndex + 1, valueBencode);
          break;
        default:
          throw new RuntimeException("illegal type in list bencode type");
      }
      dic.put(key.getString(), valueBencode);
    }

    return parsingCodeIndex + 1;
  }
}
