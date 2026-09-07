package com.example.demo.model

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "insurance_companies")
class InsuranceCompany(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "insurance_company_id", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    var clinic: Clinic? = null,

    @Column(name = "name", nullable = false, length = 150)
    var name: String = ""
)