
public enum BencodeType {
    INT,
    STRING,
    LIST,
    DIC;


    public static BencodeType fromByte(byte ch) {
        if (Character.isDigit(ch)) {
            return STRING;
        } else if ('i' == ch) {
            return INT;

        } else if ('l' == ch) {
            return LIST;
        } else if ('d' == ch) {
            return DIC;
        } else {
            throw new RuntimeException("unknown char code" + Character.toString(ch));
        }
    }
}
