package com.vigilante.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Admin-managed place lists (user request): municipalities and their
 * districts are defined once in the admin panel, and the add-volunteer form
 * offers them as OPTIONAL dropdowns — no more free-text spelling chaos.
 * The Volunteer table keeps storing plain names, so Excel files and merge
 * logic are untouched.
 */
@Entity(
    tableName = "municipalities",
    indices = [Index(value = ["name"], unique = true)]
)
data class Municipality(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "districts",
    indices = [
        Index(value = ["municipalityId", "name"], unique = true),
        Index("municipalityId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = Municipality::class,
            parentColumns = ["id"],
            childColumns = ["municipalityId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class District(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val municipalityId: Long,
    val name: String
)
