package com.syndicatedhub.syndicatedfeedserver.datastorage

import scala.concurrent.{ExecutionContext, Future}

trait BaseDataStorage[KEY, ITEM] {
  def tableName: String
  // insert entry will overwrite old entry
  def insertEntry(key: KEY, item: ITEM)(implicit ec: ExecutionContext): Future[ITEM]
  def query(key: KEY)(implicit ec: ExecutionContext): Future[Map[KEY,ITEM]]
  def deleteEntry(key: KEY)(implicit ec: ExecutionContext): Future[Option[ITEM]]
}
