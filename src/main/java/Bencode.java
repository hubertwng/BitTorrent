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
          String key = (String)entry.getKey();
          builder.append(key.length()).append(":").append(key);
          Bencode bencode = (Bencode) entry.getValue();
          builder.append(bencode.encodeToString());
        }
        return builder.append("e").toString();
      case STRING:
        String str = (String) value;
        return builder.append(str.length()).append(":").append(str).toString();
      case INT:
        return builder.append("i").append((Long)value).append("e").toString();
      default:
        throw new RuntimeException("illegal bencode type:" + type);
    }
  }

  public String toSimpleJsonString() {
    switch (type) {
      case STRING:
        return value.toString();
      case INT:
        return ((Long)value).toString();
      case LIST:
        List<String> ans = new ArrayList<>();
        for (Object object: (List)value) {
          Bencode bencode = (Bencode) object;
          ans.add(bencode.toSimpleJsonString());
        }
        return gson.toJson(ans);
      case DIC:
        Map<String, Object> jsonMap = new HashMap<>();
        for (Map.Entry entry : ((Map<String, Bencode>) value).entrySet()) {
          String key = (String)entry.getKey();
          String value = ((Bencode)entry.getValue()).toSimpleJsonString();
          jsonMap.put(key, value);
        }
        return gson.toJson(jsonMap);
      default:
        throw new RuntimeException("illegal bencode type:" + type);
    }
  }



  public static Bencode fromString(String str) {
    if (str.isEmpty()) {
      throw new RuntimeException("illegal bencode string");
    }

    BencodeType bencodeType = BencodeType.fromCharCode(str.charAt(0));
    switch (bencodeType) {
      case LIST:
        return decodeToList(str);
      case DIC:
        return decodeToDic(str);
      case STRING:
        return decodeToString(str);
      case INT:
        return decodeToInt(str);
      default:
        throw new RuntimeException("unsupport bencode type:" + bencodeType);
    }

  }

  private static Bencode decodeToList(String bencodedString) {
    Bencode bencode = new Bencode();
    bencode.setType(BencodeType.LIST);

    if (bencodedString.isEmpty()) {
      bencode.setValue(new ArrayList<>());
      return bencode;
    }
    parseNextList(bencodedString, 1, bencode);

    return bencode;
  }

  private static Bencode decodeToString(String code) {
    Bencode bencode = new Bencode();
    bencode.setType(BencodeType.STRING);
    parseNextString(code, 1, bencode);
    return bencode;

  }

  private static Bencode decodeToInt(String code) {
    Bencode bencode = new Bencode();
    bencode.setType(BencodeType.INT);
    parseNextInt(code, 1, bencode);
    return bencode;
  }

  private static Bencode decodeToDic(String code) {
    Bencode bencode = new Bencode();
    bencode.setType(BencodeType.DIC);
    parseNextDic(code, 1, bencode);

    return bencode;
  }
 

  private static int parseNextString(String bencodedString, int parsingCodeIndex, Bencode bencode) {
    if (!Character.isDigit(bencodedString.charAt(parsingCodeIndex))) {
      throw new RuntimeException("illegal string type bencode");
    }
    int lengStartCodeIndex = parsingCodeIndex;
    int lengthEndCodeIndex = lengStartCodeIndex + 1;
    while (':' != bencodedString.charAt(lengthEndCodeIndex) && lengthEndCodeIndex < bencodedString.length()) {
      lengthEndCodeIndex++;
    }
    int length = Integer.parseInt(bencodedString.substring(lengStartCodeIndex, lengthEndCodeIndex));
    int stringStartCodeIndex = lengthEndCodeIndex + 1;
    bencode.setValue(bencodedString.substring(stringStartCodeIndex, stringStartCodeIndex + length));
    return stringStartCodeIndex + length;
  }

  private static int parseNextInt(String bencodedString, int parsingCodeIndex, Bencode bencode) {
    if ('i' != bencodedString.charAt(parsingCodeIndex)) {
      throw new RuntimeException("illegal int type bencode");
    }
    int intStartCodeIndex = parsingCodeIndex + 1;
    int intEndCodeIndex = intStartCodeIndex + 1;
    while ('e' != bencodedString.charAt(intEndCodeIndex) && intEndCodeIndex < bencodedString.length()) {
      intEndCodeIndex++;
    }
    bencode.setValue(Long.parseLong(bencodedString.substring(intStartCodeIndex, intEndCodeIndex)));
    return intEndCodeIndex + 1;
  }

  private static int parseNextList(String bencodedString, int parsingCodeIndex, Bencode bencode) {
    List<Bencode> primitiveBencodes = new ArrayList<>();
    bencode.setValue(primitiveBencodes);
    while ('e' != bencodedString.charAt(parsingCodeIndex) && parsingCodeIndex < bencodedString.length()) {
      BencodeType type = BencodeType.fromCharCode(bencodedString.charAt(parsingCodeIndex));
      Bencode nextBencode = new Bencode();
      nextBencode.setType(type);
      switch (type) {
        case INT:
          parsingCodeIndex = parseNextInt(bencodedString, parsingCodeIndex, nextBencode);
          break;
        case STRING:
          parsingCodeIndex = parseNextString(bencodedString, parsingCodeIndex, nextBencode);
          break;
        case LIST:
          parsingCodeIndex = parseNextList(bencodedString, parsingCodeIndex + 1, nextBencode);
          break;
        case DIC:
          parsingCodeIndex = parseNextInt(bencodedString, parsingCodeIndex + 1, nextBencode);
          break;
        default:
          throw new RuntimeException("unspport type in list bencode type");

      }
      primitiveBencodes.add(nextBencode);
    }
    return parsingCodeIndex + 1;
  }

  private static int parseNextDic(String bencodedString, int parsingCodeIndex, Bencode dicBencode) {
    Map<String, Bencode> dic = new HashMap<>();
    dicBencode.setValue(dic);
    while ('e' != bencodedString.charAt(parsingCodeIndex) && parsingCodeIndex < bencodedString.length()) {
      BencodeType keyType = BencodeType.fromCharCode(bencodedString.charAt(parsingCodeIndex));
      if (!BencodeType.STRING.equals(keyType)) {
        throw new RuntimeException("collection dic bencode's key must only string but is " + keyType);
      }
      Bencode key = new Bencode();
      key.setType(keyType);
      parsingCodeIndex = parseNextString(bencodedString, parsingCodeIndex, key);

      if (parsingCodeIndex >= bencodedString.length()) {
        throw new RuntimeException("collection dic bencode's must have a value");
      }
      BencodeType valueType = BencodeType.fromCharCode(bencodedString.charAt(parsingCodeIndex));
      Bencode valueBencode = new Bencode();
      valueBencode.setType(valueType);
      switch (valueType) {
        case STRING:
          parsingCodeIndex = parseNextString(bencodedString, parsingCodeIndex, valueBencode);
          break;
        case INT:
          parsingCodeIndex = parseNextInt(bencodedString, parsingCodeIndex, valueBencode);
          break;
        case LIST:
          parsingCodeIndex = parseNextList(bencodedString, parsingCodeIndex + 1, valueBencode);
          break;
        case DIC:
          parsingCodeIndex = parseNextDic(bencodedString, parsingCodeIndex + 1, valueBencode);
          break;
        default:
          throw new RuntimeException("unspport type in list bencode type");       
      }
      dic.put(key.getString(), valueBencode);
    }
    
  return parsingCodeIndex + 1;
  }
}
