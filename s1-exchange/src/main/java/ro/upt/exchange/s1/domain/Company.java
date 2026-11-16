package ro.upt.exchange.s1.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Table {@code companies}: listed companies (seed data). */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 60)
    private String sector;

    @Column(length = 60)
    private String country;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "listed_at", nullable = false, insertable = false, updatable = false)
    private LocalDate listedAt;

    public Company(String name, String sector, String country, String description) {
        this.name = name;
        this.sector = sector;
        this.country = country;
        this.description = description;
    }
}
