package com.example.demo.model

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "specialties", uniqueConstraints = [UniqueConstraint(name = "uk_specialty_name", columnNames = ["name"])])
class Specialty(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "specialty_id", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(nullable = false, unique = true, length = 100)
    var name: String = ""
)