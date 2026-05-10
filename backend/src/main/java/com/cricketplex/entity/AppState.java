package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppState {

    @Id
    @Column(name = "key", length = 64)
    private String key;

    @Column(name = "value", nullable = false)
    private String value;
}
