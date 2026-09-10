package com.example.demo.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "clinics")
class Clinic(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "clinic_id", updatable = false, nullable = false)
    val id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_user_id", referencedColumnName = "user_id", nullable = false, unique = true)
    var user: User? = null,

    @Column(name = "clinic_name", nullable = false, length = 100)
    var clinicName: String = "",

    @Column(name = "phone_number", length = 10)
    var phoneNumber: String? = null,

    @ElementCollection
    @CollectionTable(name = "clinic_social_links", joinColumns = [JoinColumn(name = "clinic_id")])
    @Column(name = "social_link")
    var socialLinks: MutableList<String> = mutableListOf(),

    @Column(name = "detailed_address", length = 255)
    var detailedAddress: String? = null,

    @Column(name = "working_hours", length = 255)
    var workingHours: String? = null,

    @Column(name = "checking_fee", precision = 10, scale = 2)
    var checkingFee: BigDecimal? = null,

    @Column(name = "rating", precision = 2, scale = 1)
    var rating: BigDecimal = BigDecimal.valueOf(0.0),

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "application_status", nullable = false, length = 20, columnDefinition = "varchar(20) default 'PENDING'")
    var applicationStatus: ClinicApplicationStatus = ClinicApplicationStatus.PENDING,

    @Column(name = "brand", length = 150)
    var brand: String? = null,

    @Column(name = "branch_count", nullable = false, columnDefinition = "integer default 1")
    var branchCount: Int = 1,

    @Column(name = "currency", length = 10)
    var currency: String? = "JOD",

    @Column(name = "tax_registration", length = 100)
    var taxRegistration: String? = null,

    @Column(name = "commission_rate", precision = 7, scale = 4, nullable = false, columnDefinition = "numeric(7,4) default 0.0")
    var commissionRate: BigDecimal = BigDecimal.ZERO,

    @Column(name = "overridden_commission_rate", precision = 7, scale = 4)
    var overriddenCommissionRate: BigDecimal? = null,

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    var rejectionReason: String? = null
) {
    fun effectiveCommissionRate(): BigDecimal = overriddenCommissionRate ?: commissionRate

    fun submittedAt(): Instant = user?.createdAt ?: Instant.EPOCH
}