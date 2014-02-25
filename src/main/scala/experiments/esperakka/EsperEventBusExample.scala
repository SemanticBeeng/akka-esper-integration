package experiments.esperakka

import akka.event.ActorEventBus
import scala.beans.BeanProperty
import akka.actor.{Actor, Props, ActorSystem}
import com.gensler.scalavro.util.Union.union
import com.gensler.scalavro.util.Union

//
// some sample event classes, @BeanProperty required to be a regular java bean as expected by Esper
//

case class Price(@BeanProperty symbol: String, @BeanProperty price: Double)
case class Buy(@BeanProperty symbol: String, @BeanProperty price: Double, @BeanProperty amount: Long)
case class Sell(@BeanProperty symbol: String, @BeanProperty price: Double, @BeanProperty amount: Long)

/**
 * An ActorEventBus routing events to subscribers via Esper rules.
 * The sample esper rules implement a simplified trading algorithm
 * @param windowSize  moving window size for the sample trading algorithm
 * @param orderSize   number of shares for buy orders
 */
class EsperEventBusExample(windowSize:Int, orderSize: Int) extends ActorEventBus with EsperClassification {

  type EsperEvents = union[Price] #or [Sell] #or [Buy]

  // unfortunately this has to be defined here where the type evidence is available to the compiler.
  // this will be the exact same in every event bus using EsperClassification, you can copy/paste (YUCK!) it.
  override def esperEventTypes = new Union[EsperEvents]

  //
  // generate a Buy order for a quantity of orderSize at the newest price, if the simple average of the last windowSize prices is greater than the oldest price in that window
  //

  // for debugging only
  epl("Feed", "select * from Price")

  // this will delay the Price stream by windowSize - 1:
  // the price at position latest - windowSize will fall out of the window into the Delayed stream
  epl(s"insert rstream into Delayed select rstream symbol,price from Price.std:groupwin(symbol).win:length(${windowSize-1})")

  // after every windowSize prices for a symbol, the average is inserted into the Averages stream
  epl(s"insert into Averages select symbol,avg(price) as price from Price.std:groupwin(symbol).win:length_batch($windowSize) group by symbol")

  // the join is only triggered by a new average (because it has the unidrectional keyword), which (see above) is only
  // generated after windowSize prices for a given symbol has been seen (due to the length_batch window)
  epl(
    s"""
      insert into Buy
      select p.symbol, p.price, $orderSize as amount
      from Price.std:unique(symbol) p
      join Delayed.std:unique(symbol) d on d.symbol = p.symbol
      join Averages a unidirectional on a.symbol = p.symbol
      where a.price > d.price
    """)
}

class BuyingActor extends Actor {
  def receive = {
    case EventBean(_,Buy(sym,price,amt)) => println(s"Buyer got a new order: $amt $sym @ $$$price")
  }
}

class Debugger extends Actor {
  def receive = {
    case EventBean(evtType,underlying) => println(s"DEBUG -  ${evtType.getName} : $underlying")
  }
}

class EsperEventBusWithModuleExample extends ActorEventBus with EsperClassification with ExampleEsperModule

object EsperEventBusApp extends App {
  // set up the event bus and actor(s)
  val system = ActorSystem()
  //val evtBus = new EsperEventBusExample(4,1000)
  val evtBus = new EsperEventBusWithModuleExample
  val buyer = system.actorOf(Props(classOf[BuyingActor]))
  val debugger = system.actorOf(Props(classOf[Debugger]))

  // subscribe BuyingActor to buy orders
  evtBus.subscribe(buyer, "Buy")

  // subscribe to various intermediate streams for debugging/demonstration purposes
  evtBus.subscribe(debugger, "Feed")
  evtBus.subscribe(debugger, "Delayed")
  evtBus.subscribe(debugger, "Averages")

  val prices = Array(
    Price("BP", 7.61), Price("RDSA", 2101.00), Price("RDSA", 2209.00),
    Price("BP",7.66), Price("BP", 7.64), Price("BP", 7.67)
  )

  // feed in the market data
  prices foreach (evtBus.publishEvent(_))

  // demonstrate we can also submit Sells and Buys to the event bus, thanks to the union type
  evtBus.publishEvent(Buy("IBM",182.79, 100))
  evtBus.publishEvent(Sell("NBG",4.71, 1000))
}

