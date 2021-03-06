/*
 * Scala.js (https://www.scala-js.org/)
 *
 * Copyright EPFL.
 *
 * Licensed under Apache License 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package org.scalajs.junit

import com.novocode.junit.{Ansi, RichLogger, RunSettings}
import Ansi._
import sbt.testing._
import scala.scalajs.reflect.Reflect
import scala.util.{Try, Success, Failure}

final class JUnitTask(val taskDef: TaskDef, runSettings: RunSettings)
    extends sbt.testing.Task {

  def tags: Array[String] = Array.empty

  var failed = 0
  var ignored = 0
  var total = 0

  def execute(eventHandler: EventHandler, loggers: Array[Logger],
      continuation: Array[Task] => Unit): Unit = {
    continuation(execute(eventHandler, loggers))
  }

  def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    val fullClassName = taskDef.fullyQualifiedName
    val richLogger = new RichLogger(loggers, runSettings, fullClassName)

    def infoOrDebug(msg: String): Unit = {
      if (runSettings.verbose)
        richLogger.info(msg)
      else
        richLogger.debug(msg)
    }

    infoOrDebug(c("Test run started", BLUE))

    val bootstrapperName = fullClassName + "$scalajs$junit$bootstrapper"

    val startTime = System.nanoTime

    def errorWhileLoadingClass(t: Throwable): Unit = {
      richLogger.error("Error while loading test class: " + fullClassName)
      richLogger.trace(t)
      val selector = new TestSelector(fullClassName)
      val optThrowable = new OptionalThrowable(t)
      val ev = new JUnitEvent(taskDef, Status.Failure, selector, optThrowable)
      eventHandler.handle(ev)
    }

    Try {
      Reflect
        .lookupLoadableModuleClass(bootstrapperName + "$")
        .getOrElse(throw new ClassNotFoundException(s"Cannot find $bootstrapperName$$"))
        .loadModule()
    } match {
      case Success(bootstrapper: Bootstrapper) =>
        new JUnitExecuteTest(this, runSettings, bootstrapper,
            richLogger, eventHandler).executeTests()

      case Success(_) =>
        val msg = s"Expected $bootstrapperName to extend Bootstrapper"
        errorWhileLoadingClass(new Exception(msg))

      case Failure(exception) =>
        errorWhileLoadingClass(exception)
    }

    val time = System.nanoTime - startTime

    val msg = {
      c("Test run finished: ", BLUE) +
      c(s"$failed failed", if (failed == 0) BLUE else RED) +
      c(s", ", BLUE) +
      c(s"$ignored ignored", if (ignored == 0) BLUE else YELLOW) +
      c(s", $total total, ${time.toDouble / 1000000000}s", BLUE)
    }

    infoOrDebug(msg)

    Array()
  }
}
