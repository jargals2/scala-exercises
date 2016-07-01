/*
 * scala-exercises-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaExercises.exercises.persistence.domain

import shared._
import shapeless._
import ops.record._
import doobie.imports._

case class UserProgress(
  id:           Long,
  userId:       Long,
  libraryName:  String,
  sectionName:  String,
  method:       String,
  version:      Int,
  exerciseType: ExerciseType = Other,
  args:         List[String],
  succeeded:    Boolean
)
case class ExerciseEvaluation(
  libraryName:  String,
  sectionName:  String,
  method:       String,
  version:      Int,
  exerciseType: ExerciseType = Other,
  args:         List[String],
  succeeded:    Boolean
)

object SaveUserProgress {

  case class Request(
      user:         User,
      libraryName:  String,
      sectionName:  String,
      method:       String,
      version:      Int,
      exerciseType: ExerciseType,
      args:         List[String],
      succeeded:    Boolean
  ) {

    def asUserProgress(id: Long): UserProgress =
      UserProgress(id, user.id, libraryName, sectionName, method, version, exerciseType, args, succeeded)
  }

}

object UserProgressQueries {

  val userProgressGen = LabelledGeneric[UserProgress]
  val userProgressKeys = Keys[userProgressGen.Repr]
  val allFields: List[String] =
    userProgressKeys()
      .to[List]
      .map { case Symbol(s) ⇒ s.toLowerCase }

  object Implicits {
    implicit val ExerciseTypeMeta: Meta[ExerciseType] =
      Meta[String].nxmap(
        ExerciseType.fromString,
        ExerciseType.toString
      )
  }

  private[this] val commonFindBy =
    """SELECT
       id, userid, libraryname, sectionname, method, version, exercisetype, args, succeeded
       FROM "userProgress""""

  val all = commonFindBy

  val findById = s"$commonFindBy WHERE id = ?"

  val findByUserId = s"$commonFindBy WHERE userId = ?"

  val findEvaluationsBySection = s"""$findByUserId AND libraryname = ? AND sectionname = ?"""

  val findEvaluationByVersion = s"""$findEvaluationsBySection AND method = ? AND version = ?"""

  val findByUserIdAggregated =
    s"""
       SELECT libraryname, count(sectionname), bool_and(succeeded)
       FROM "userProgress"
       WHERE userId=?
       GROUP BY libraryname
     """

  val findByLibrary =
    s"""SELECT
       sectionname, bool_and(succeeded)
       FROM "userProgress"
       WHERE userId = ? AND libraryname=?
       GROUP BY sectionname"""

  val findBySection =
    s"""$commonFindBy WHERE userId = ? AND libraryName = ? AND sectionName = ?"""

  val findByExerciseVersion =
    s"""$commonFindBy WHERE userId = ? AND libraryName = ? AND sectionName = ? AND method = ? AND version = ?"""

  val update =
    s"""
          UPDATE "userProgress"
          SET libraryName = ?,
          sectionName = ?,
          method = ?,
          version = ?,
          exerciseType = ?,
          args = ?,
          succeeded = ?
          WHERE userId = ? AND libraryName = ? AND sectionName = ? AND method = ?
    """

  val insert =
    s"""
          INSERT INTO "userProgress"(${allFields.tail.mkString(", ")})
          VALUES(?,?,?,?,?,?,?,?)
    """

  val deleteById = s"""DELETE FROM "userProgress" WHERE id = ?"""

  val deleteAll = s"""DELETE FROM "userProgress""""

  val findLastSeenSection = s"""
      |SELECT sectionName
      |FROM "userProgress"
      |WHERE userId = ?
      |  AND libraryName LIKE ?
      |ORDER BY "updatedAt" DESC
      |LIMIT 1;
  """.stripMargin
}
