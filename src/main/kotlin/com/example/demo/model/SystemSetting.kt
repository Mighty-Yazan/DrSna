package com.example.demo.model

import jakarta.persistence.*

@Entity
@Table(name = "system_settings")
class SystemSetting(
    @Id
    @Column(name = "setting_key", nullable = false, length = 50)
    val key: String = "",

    @Column(name = "setting_value", nullable = false)
    var value: String = ""
)