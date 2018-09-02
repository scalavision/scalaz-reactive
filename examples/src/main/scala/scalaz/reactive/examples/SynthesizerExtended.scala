package scalaz.reactive.examples

//import java.io.IOException
//import scalaz.zio.console._

import scalaz.reactive._
import scalaz.zio.{App, IO}

import scala.io.StdIn

/**
  * Example from https://wiki.haskell.org/FRP_explanation_using_reactive-banana
  *
  * Extended with
  *
  * - the ability to quit the app
  * - the tones and octaves are extracted into separate behaviours
  *
  *
  */

object SynthesizerExtended extends App {

  type Octave = Int

  sealed trait KeyAction

  sealed abstract class Pitch(val tone: Char) extends KeyAction {
    def change: Char => Pitch = {
      case 'a' => PA
      case 'b' => PB
    }
  }
  final case object PA extends Pitch('a')
  final case object PB extends Pitch('b')
  final case object PC extends Pitch('c')
  final case object PD extends Pitch('d')
  final case object PE extends Pitch('e')
  final case object PF extends Pitch('f')
  final case object PG extends Pitch('g')

  sealed abstract class OctaveAction(val key: Char) extends KeyAction
  object OctaveAction {
    final case object Up extends OctaveAction('+')
    final case object Down extends OctaveAction('-')
  }

  sealed abstract class AppAction(val key: Char) extends KeyAction
  object AppAction {
    final case object Quit extends AppAction('q')
    final case object Init extends AppAction(key = 's')
  }

  val toneMap: Map[Char, Pitch] = Map(
    PA.tone -> PA,
    PB.tone -> PB,
    PC.tone -> PC,
    PD.tone -> PD,
    PE.tone -> PE,
    PF.tone -> PF,
    PG.tone -> PG
  )

  val octaveMap: Map[Char, OctaveAction] = Map(
    OctaveAction.Up.key -> OctaveAction.Up,
    OctaveAction.Down.key -> OctaveAction.Down
  )

  val appMap: Map[Char, AppAction] = Map(
    AppAction.Quit.key -> AppAction.Quit
  )

  val eKey : Event[Char] = {
    val nextChar: IO[Void, Char] = IO.sync {
      println("hit a note, or octave, q to quit")
      StdIn.readChar()
    }

    Event(nextChar.flatMap { c =>
      Time.now.map ((_, Reactive(c, eKey)))
    })

  }

  def keyChange[A](
    eKey: Event[Char],
    default: A,
    keyHandler: Char => Option[A => A],
    timeFn: A => TimeFun[A]
  ): Behaviour[A] = {

    val keyAction: Event[A => A] = Event.joinMaybes( eKey.map { k => keyHandler(k) } )

    Behaviour(Reactive(
      TimeFun.K(default),
      Event.accumE(default)(keyAction).map(timeFn)
    ))

  }

  val octaveKeyHandler: Char => Option[Octave => Octave] = {
    case OctaveAction.Up.key => Some((x: Octave) => if(x >= 10) 10 else x + 1)
    case OctaveAction.Down.key => Some((x: Octave) => if(x <= 0) 0 else x - 1)
    case _ => None
  }

  val octaveTimeFn: Octave => TimeFun[Octave] = octave => TimeFun.K(octave)

  def octChange(eKey: Event[Char]): Behaviour[Octave] =
    keyChange[Octave](eKey, 0, octaveKeyHandler, octaveTimeFn)

  val toneKeyHandler: Char => Option[Pitch => Pitch] = {
    case c: Char if toneMap.isDefinedAt(c) => Some( (_: Pitch) => toneMap(c))
    case _ => None
  }

  val toneTimeFn: Pitch => TimeFun[Pitch] = pitch => TimeFun.K(pitch)

  def toneChange(eKey: Event[Char]): Behaviour[Pitch] =
    keyChange[Pitch](eKey, PA, toneKeyHandler, toneTimeFn)

  val appKeyHandler: Char => Option[AppAction => AppAction] = {
    case c: Char if appMap.isDefinedAt(c) => Some((_: AppAction) => appMap(c))
    case _ => None
  }

  val appTimeFn: AppAction => TimeFun[AppAction] = appAction => TimeFun.K(appAction)

  def appChange(eKey: Event[Char]): Behaviour[AppAction] =
    keyChange[AppAction](eKey, AppAction.Init, appKeyHandler, appTimeFn)

//  def beep(tone: Behaviour[Pitch], octave: Behaviour[Octave]) = {
//    println(s"play sound at: ${octave.value} ${tone.value}")
//  }

  def beep(tone: Behaviour[Pitch], octave: Behaviour[Octave]): Behaviour[(Pitch, Octave)] = {
//    tone.map { p =>
//      octave.map { o =>
//        (p,o)
//      }
//    }

      tone.ap[(Pitch, Octave)](octave.map { o => (_, o)})
  }

  private def runApp(): IO[Void, Unit] = {

    val octSink: Behaviour[Octave] = octChange(eKey)
    val toneSink: Behaviour[Pitch] = toneChange(eKey)
    val appSink: Behaviour[AppAction] = appChange(eKey)

    //Sink.sinkB(beep(toneSink, octSink), (tn: TimeFun[(Pitch, Octave)]) => Time.now.map{t => println(s"Tone ${tn.apply(t)}")})

    for {
      _ <- Sink.sinkB(toneSink, (tn: TimeFun[Pitch]) => Time.now.map {t => println(s"Tone is now ${tn.apply(t)}")}).fork
      _ <- Sink.sinkB(octSink, (tn: TimeFun[Octave]) => Time.now.map {t => println(s"Octave is ${tn.apply(t)}")}).fork
      _ <- Sink.sinkB(appSink, (tn: TimeFun[AppAction]) => Time.now.map{t => println(s"App Status is now ${tn.apply(t)}")}).fork
    } yield ()

    //Sink.sinkB(toneSink, (tn: TimeFun[Pitch]) => Time.now.map{t => println(s"Tone is now ${tn.apply(t)}")})

  }

  override def run(args: List[String]): IO[Nothing, SynthesizerExtended.ExitStatus] =
    runApp().attempt.map(_.fold(_ => 1, _ => 0)).map(ExitStatus.ExitNow(_))

}


/*

Old stuff kept for reference ...

  def octaveChange(eKey: Event[Char]): Behaviour[Octave] = {
    val octaveKeyAction: Event[Octave => Octave] = Event.joinMaybes(
      eKey.map {
        case OctaveAction.Up.key => Some((x: Octave) => x + 1)
        case OctaveAction.Down.key => Some((x: Octave) => x - 1)
      }
    )

    Behaviour(Reactive(
      TimeFun.K(0),
      Event.accumE(0)(octaveKeyAction).map((x: Octave) => TimeFun.K(x))
    ))
  }
 */