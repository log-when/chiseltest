// SPDX-License-Identifier: Apache-2.0

package chiseltest.formal

import chisel3.Module
import chiseltest.HasTestName
import chiseltest.formal.backends.FormalEngineAnnotation
import chiseltest.internal.TestEnvInterface
import chiseltest.simulator.{Compiler, WriteVcdAnnotation}
import firrtl.{AnnotationSeq, CircuitState}
import firrtl.annotations.NoTargetAnnotation
import firrtl.transforms.formal.DontAssertSubmoduleAssumptionsAnnotation
import sys.process._
import java.io._
import jhoafparser.parser.HOAFParser

sealed trait FormalOp extends NoTargetAnnotation
case class BoundedCheck(kMax: Int = -1) extends FormalOp

/** Specifies how many cycles the circuit should be reset for. */
case class ResetOption(cycles: Int = 1) extends NoTargetAnnotation {
  require(cycles >= 0, "The number of cycles must not be negative!")
}

class FailedBoundedCheckException(val message: String, val failAt: Int) extends Exception(message)
private[chiseltest] object FailedBoundedCheckException {
  def apply(module: String, failAt: Int): FailedBoundedCheckException = {
    val msg = s"[$module] found an assertion violation $failAt steps after reset!"
    new FailedBoundedCheckException(msg, failAt)
  }
}

/** Adds the `verify` command for formal checks to a ChiselScalatestTester */
trait Formal { this: HasTestName =>
  def verify[T <: Module](dutGen: => T, annos: AnnotationSeq): Unit = {
    val withTargetDir = TestEnvInterface.addDefaultTargetDir(getTestName, annos)
    Formal.verify(dutGen, withTargetDir)
  }
}

/** An _escape hatch_ to disable more pessimistic modelling of undefined values. */
case object DoNotModelUndef extends NoTargetAnnotation

/** Disables firrtl optimizations when converting to a SMT/Btor2 system.
  * This is an escape hatch in case you suspect optimizations of doing something wrong!
  * Normally this annotation should *not* be needed!
  */
case object DoNotOptimizeFormal extends NoTargetAnnotation

private object Formal {
  def verify[T <: Module](dutGen: => T, annos: AnnotationSeq): Unit = {
    val ops = getOps(annos)
    assert(ops.nonEmpty, "No verification operation was specified!")
    val withDefaults = addDefaults(annos)

    // elaborate the design and compile to low firrtl
    val (highFirrtl, _) = Compiler.elaborate(() => dutGen, withDefaults)
    val lowFirrtl = Compiler.toLowFirrtl(highFirrtl, Seq(DontAssertSubmoduleAssumptionsAnnotation))

    // add reset assumptions
    val withReset = AddResetAssumptionPass.execute(lowFirrtl)

    /*val SVAAnnos : AnnotationSeq = withReset.annotations.filter {_.isInstanceOf[SVAAnno]}
    val teemp = SVAAnnos(0).asInstanceOf[SVAAnno].toElementSeq().toSeq
    println(teemp)
    val target2p = SVAAnno.generateMap2p(teemp)
    println(target2p)
    val syntaxTree = SVAAnno.toSVATree(teemp)
    println(SVAAnno.toSVATree(teemp))
    val psl = SVAAnno.toPSL(syntaxTree,target2p)*/
    
    //!!!!!!!!!!!!!!!!!!!!
    //Attention, there chooses a special psl to test!
    //!!!!!!!!!!!!!!!!!!!!
    /*val psl = "{(p | q)[*]}<>->Gp"
    println(SVAAnno.toPSL(syntaxTree,target2p))

    val targetDir = Compiler.requireTargetDir(annos)
    val cmd = Seq("ltl2tgba","-B","-D", "-f", psl)
    val r = os.proc(cmd).call(cwd = targetDir, check = false)         
    println("---")           
    println(r.out.string)
    println("---")

    val is = new ByteArrayInputStream(r.out.string.getBytes())
    // 转 BufferedInputStream
    val bis = new BufferedInputStream(is)    
    // 打印
    //Stream.continually(bis.read()).takeWhile(_ != -1).foreach(println(_))
    val h = new hoaParser()
    HOAFParser.parseHOA(bis,h)    
    bis.close()
    is.close()
    
    h.partialDeterministic()
    println("//////////////////////////")
    println(h.transitionFunc)
    h.addAuxVar()
    println("//////////////////////////")
    println(h.transitionFunc)*/



    
     //val hoaParser = new ParserHOA()
    // val SVAAnnos_ = SVAAnnos.toSeq.flatMap{_.asInstanceOf[SVAAnno].flat()}.filter(_.asInstanceOf[SVAAnno].sameModule)
    // println(SVAAnnos.toSeq)
    /*SVAAnnos_.map
    {
      case a:SVAAnno => {
        println(a)
        a
      }
      case _ => 
    }*/
    //println(SVAAnnos_.toSeq.toString)
    // execute operations
    val resetLength = AddResetAssumptionPass.getResetLength(withDefaults)
    ops.foreach(executeOp(withReset, resetLength, _))
  }

  val DefaultEngine: FormalEngineAnnotation = Z3EngineAnnotation
  def addDefaults(annos: AnnotationSeq): AnnotationSeq = {
    Seq(addDefaultEngine(_), addWriteVcd(_)).foldLeft(annos)((old, f) => f(old))
  }
  def addDefaultEngine(annos: AnnotationSeq): AnnotationSeq = {
    if (annos.exists(_.isInstanceOf[FormalEngineAnnotation])) { annos }
    else { DefaultEngine +: annos }
  }
  def addWriteVcd(annos: AnnotationSeq): AnnotationSeq = {
    if (annos.contains(WriteVcdAnnotation)) { annos }
    else { WriteVcdAnnotation +: annos }
  }
  def getOps(annos: AnnotationSeq): Seq[FormalOp] = {
    annos.collect { case a: FormalOp => a }.distinct
  }
  def executeOp(state: CircuitState, resetLength: Int, op: FormalOp): Unit = op match {
    case BoundedCheck(kMax) =>
      backends.Maltese.bmc(state.circuit, state.annotations, kMax = kMax, resetLength = resetLength)
  }
}
