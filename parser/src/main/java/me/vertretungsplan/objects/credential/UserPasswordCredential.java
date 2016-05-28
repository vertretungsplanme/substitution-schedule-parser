package me.vertretungsplan.objects.credential;

public class UserPasswordCredential extends BaseCredential {
    private String username;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getHash() {
        return hash(username + ":" + password);
    }
}
