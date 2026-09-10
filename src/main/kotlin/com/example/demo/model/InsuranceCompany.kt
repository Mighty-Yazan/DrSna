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
    var name: String = "",

    @Column(name = "coverage_tier", length = 255)
    var coverageTier: String? = null,

    @Column(name = "copay", length = 50)
    var copay: String? = null,

    @Column(name = "phone", length = 20)
    var phone: String? = null,

    @Column(name = "portal_url", length = 500)
    var portalUrl: String? = null,

    @Column(name = "instant_pre_approval", nullable = false, columnDefinition = "boolean default false")
    var instantPreApproval: Boolean = false,

    @Column(name = "network", length = 150)
    var network: String? = null,

    @Column(name = "code", length = 10)
    var code: String? = null,

    @Column(name = "badge_bg", length = 50)
    var badgeBg: String? = null,

    @Column(name = "badge_text", length = 50)
    var badgeText: String? = null,

    @Column(name = "direct_billing_type", length = 100)
    var directBillingType: String? = null,

    @Column(name = "status", length = 50)
    var status: String? = null
)