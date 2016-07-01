/*
 * scala-exercises-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaExercises.exercises.persistence.repositories

import org.scalaExercises.exercises.persistence.PersistenceModule
import org.scalaExercises.exercises.persistence.domain._
import org.scalaExercises.exercises.persistence.repositories.UserProgressRepository._
import doobie.imports._
import org.scalaExercises.exercises.persistence.domain.{ UserProgressQueries ⇒ Q }
import Q.Implicits._
import doobie.contrib.postgresql.pgtypes._
import shared._

trait UserProgressRepository {
  def create(request: SaveUserProgress.Request): ConnectionIO[UserProgress]

  def findById(id: Long): ConnectionIO[Option[UserProgress]]

  def delete(id: Long): ConnectionIO[Int]

  def update(request: SaveUserProgress.Request): ConnectionIO[UserProgress]

  def upsert(request: SaveUserProgress.Request): ConnectionIO[UserProgress]

  def getExerciseEvaluation(user: User, libraryName: String, sectionName: String, method: String, version: Int): ConnectionIO[Option[UserProgress]]

  def getExerciseEvaluations(user: User, libraryName: String, sectionName: String): ConnectionIO[List[UserProgress]]

  def getLastSeenSection(user: User, libraryName: String): ConnectionIO[Option[String]]

  def deleteAll(): ConnectionIO[Int]
}

class UserProgressDoobieRepository(implicit persistence: PersistenceModule) extends UserProgressRepository {
  override def create(request: SaveUserProgress.Request): ConnectionIO[UserProgress] = {
    val SaveUserProgress.Request(user, libraryName, sectionName, method, version, exerciseType, args, succeeded) = request
    persistence
      .updateWithGeneratedKeys[InsertParams, UserProgress](
        Q.insert,
        Q.allFields,
        (user.id, libraryName, sectionName, method, version, exerciseType, args, succeeded)
      )
  }

  override def findById(id: Long): ConnectionIO[Option[UserProgress]] = {
    persistence.fetchOption[(Long), UserProgress](Q.findById, id)
  }

  override def delete(id: Long): ConnectionIO[Int] = {
    persistence.update[(Long)](Q.deleteById, id)
  }

  override def update(request: SaveUserProgress.Request): ConnectionIO[UserProgress] = {
    val SaveUserProgress.Request(user, libraryName, sectionName, method, version, exerciseType, args, succeeded) = request
    persistence
      .updateWithGeneratedKeys[UpdateParams, UserProgress](
        Q.update,
        Q.allFields,
        (libraryName, sectionName, method, version, exerciseType, args, succeeded, user.id, libraryName, sectionName, method)
      )
  }

  override def upsert(request: SaveUserProgress.Request): ConnectionIO[UserProgress] = {
    val SaveUserProgress.Request(user, libraryName, sectionName, method, version, _, _, _) = request
    getExerciseEvaluation(user, libraryName, sectionName, method, version) flatMap {
      case None        ⇒ create(request)
      case Some(userP) ⇒ update(request)
    }
  }

  override def getExerciseEvaluation(
    user:        User,
    libraryName: String,
    sectionName: String,
    method:      String,
    version:     Int
  ): ConnectionIO[Option[UserProgress]] =
    persistence.fetchOption[FindEvaluationByVersionParams, UserProgress](
      Q.findEvaluationByVersion, (user.id, libraryName, sectionName, method, version)
    )

  override def getExerciseEvaluations(user: User, libraryName: String, sectionName: String): ConnectionIO[List[UserProgress]] =
    persistence.fetchList[FindEvaluationsBySectionParams, UserProgress](
      Q.findEvaluationsBySection, (user.id, libraryName, sectionName)
    )

  override def getLastSeenSection(user: User, libraryName: String): ConnectionIO[Option[String]] = {
    persistence.fetchOption[FindLastSeenSectionParams, String](
      Q.findLastSeenSection, (user.id, libraryName)
    )
  }

  override def deleteAll(): ConnectionIO[Int] = {
    persistence.update(Q.deleteAll)
  }
}

object UserProgressRepository {
  type UpdateParams = (String, String, String, Int, ExerciseType, List[String], Boolean, Long, String, String, String)
  type InsertParams = (Long, String, String, String, Int, ExerciseType, List[String], Boolean)
  type FindEvaluationByVersionParams = (Long, String, String, String, Int)
  type FindEvaluationsBySectionParams = (Long, String, String)
  type FindLastSeenSectionParams = (Long, String)
  type CompletedCountParams = (Long, String, String)

  implicit def instance(implicit persistence: PersistenceModule): UserProgressRepository = new UserProgressDoobieRepository
}
