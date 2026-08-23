package com.example.demo.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "Clinics")
class Clinic(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name="clinic_id", updatable = false, nullable = false)
    var id: UUID? = null,

    //remember to ask
@OneToOne(fetch= FetchType.LAZY)
    @JoinColumn(name="clinic_user_id",referencedColumnName="user_id",nullable= false,unique=true)
    var user:User? = null,

    @Column(name="clinic_name", nullable = false,length=100)
    var clinicName:String = "",

    @Column(name = "phone_number", length = 10)
    var phoneNumber: String? = null,

    @Column(name="social_Links",columnDefinition = "TEXT")
    var socialLinks: String? = null,

    //remember to ask
    @Column(name="detailed_address",length=100)
    var detailedAddress:String? = null,

    @Column(name="working_hours", length=255)
    var workingHours:String? = null,

    @Column(name = "checking_fee", precision=10,scale=2)
    var checkingFee: BigDecimal? = null,

    @Column(name="rating",precision = 2,scale=1)
    var rating: BigDecimal = BigDecimal.valueOf(0.0),

    @Column(columnDefinition = "TEXT")
    var description: String? = null,


    )
