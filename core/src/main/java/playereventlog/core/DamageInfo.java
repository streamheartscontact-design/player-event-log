package playereventlog.core;

public final class DamageInfo {
    public final String typeId;
    public final String msgId;
    public final String attacker;
    public final String attackerName;
    public final String direct;
    public final Object token;

    public DamageInfo(String typeId, String msgId, String attacker, String attackerName, String direct, Object token) {
        this.typeId = typeId;
        this.msgId = msgId;
        this.attacker = attacker;
        this.attackerName = attackerName;
        this.direct = direct;
        this.token = token;
    }
}
