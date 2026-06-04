package com.bofa.auth.jwt;

/** Minimal set of JWT claims used by the customer session token. */
public class Claims {

    private String sub;
    private long iat;
    private long exp;
    private String roles;

    public Claims() {
    }

    public Claims(String sub, long iat, long exp, String roles) {
        this.sub = sub;
        this.iat = iat;
        this.exp = exp;
        this.roles = roles;
    }

    public String getSub() {
        return sub;
    }

    public void setSub(String sub) {
        this.sub = sub;
    }

    public long getIat() {
        return iat;
    }

    public void setIat(long iat) {
        this.iat = iat;
    }

    public long getExp() {
        return exp;
    }

    public void setExp(long exp) {
        this.exp = exp;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }
}
