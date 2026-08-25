package com.example.demo.model

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "services")
class Services (
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name="service_id",updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="clinic_id",referencedColumnName = "clinic_id",nullable=false)
    var clinic: Clinic? = null,

    @Column(name = "service_name", nullable = false, length = 100)
    var serviceName: String = "",

    @Column(name="description_of_service",columnDefinition = "TEXT")
    var descriptionOfService: String? = null,
)