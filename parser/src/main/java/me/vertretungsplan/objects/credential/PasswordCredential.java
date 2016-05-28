package me.vertretungsplan.objects.credential;

public class PasswordCredential extends BaseCredential {
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getHash() {
        return hash(password);
    }
}
