package ru.mirea.cyberclub.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

/**
 * Клиент киберспортивного клуба.
 */
public class Client extends BaseEntity {

    private String nickname;
    private String fullName;
    private String phone;
    private String email;
    private LocalDate birthDate;
    private LocalDateTime createdAt;

    public Client() {
    }

    public Client(String nickname, String fullName, String phone, String email, LocalDate birthDate) {
        this.nickname = nickname;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.birthDate = birthDate;
    }

    public Client(Long id, String nickname, String fullName, String phone, String email,
                  LocalDate birthDate, LocalDateTime createdAt) {
        super(id);
        this.nickname = nickname;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.birthDate = birthDate;
        this.createdAt = createdAt;
    }

    /** Полных лет на указанную дату. */
    public int getAge(LocalDate onDate) {
        if (birthDate == null) {
            return 0;
        }
        return Period.between(birthDate, onDate).getYears();
    }

    public boolean isAdult(LocalDate onDate) {
        return getAge(onDate) >= 18;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String describe() {
        return "Клиент #" + getId() + " " + nickname + " (" + fullName + ")";
    }

    @Override
    public String toString() {
        return "Client{id=" + getId() + ", nickname=" + nickname + ", fullName=" + fullName
                + ", phone=" + phone + ", email=" + email + ", birthDate=" + birthDate + "}";
    }
}
