package com.example.demo.model

import jakarta.persistence.*
import java.io.Serializable
import java.util.UUID

@Embeddable
data class ClinicSpecialtyId(
    @Column(name = "clinic_id") var clinicId: UUID = UUID(0, 0),
    @Column(name = "specialty_id") var specialtyId: UUID = UUID(0, 0)
) : Serializable

@Entity
@Table(name = "clinic_specialties")
class ClinicSpecialty(
    @EmbeddedId var id: ClinicSpecialtyId = ClinicSpecialtyId(),
    @ManyToOne(fetch = FetchType.LAZY) @MapsId("clinicId") @JoinColumn(name = "clinic_id") var clinic: Clinic? = null,
    @ManyToOne(fetch = FetchType.LAZY) @MapsId("specialtyId") @JoinColumn(name = "specialty_id") var specialty: Specialty? = null
)