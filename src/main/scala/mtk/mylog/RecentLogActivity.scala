package mtk.mylog
 
import android.os.Bundle
import android.app.ListActivity
import android.provider.CallLog.Calls
import android.provider.ContactsContract
import android.database.Cursor
import android.view.View.OnClickListener
import android.net.Uri
import android.text.TextUtils
import android.view.{ViewGroup, View}
import android.provider.ContactsContract.PhoneLookup
import android.text.format.DateUtils
import android.widget.{ListView, ArrayAdapter, TextView}
import android.content._
import android.telephony.{SmsMessage, PhoneNumberUtils}

class RecentLogActivity extends ListActivity {
  val maxItems = 20
  val receiver = new Updater
  val smsfilter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED")

  class Updater extends BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent){
      val contactInfo = contactInfoFromSmsIntent(intent)
      setListAdapter(new ItemAdapter(reload(List(contactInfo))))
      //presume this is run in an even handler thread, not allowed to call View.invalidate()
      getListView.postInvalidate
    }
  }

  private def contactInfoFromSmsIntent(intent: Intent) = {
    val msg = SmsMessage.createFromPdu(intent.getExtras.get("pdus").asInstanceOf[Array[Object]](0).asInstanceOf[Array[Byte]])
    val source = msg.getOriginatingAddress
    new ContactInfo(source,lookupName(source),msg.getTimestampMillis,true)
  }

  override def onCreate(state: Bundle) {
    super.onCreate(state)
    setContentView(R.layout.list)
  }

  override def onResume() {
    super.onResume
    setListAdapter(new ItemAdapter(reload(List())))
    registerReceiver(receiver, smsfilter)
  }

  override def onPause(){
    unregisterReceiver(receiver);
    super.onPause
  }

  override def onListItemClick(listView: ListView, view: View, position: Int, id: Long) = {
    ShowContactOnClick.onClick(view)
  }

  private def reload(pre: List[ContactInfo]) = {
    val smss = managedQuery(Uri.parse("content://sms"), Array("address", "date"), "type < 3", null, "date DESC")
    val calls = managedQuery(Calls.CONTENT_URI, Array(Calls.NUMBER, Calls.CACHED_NAME, Calls.DATE), null, null, Calls.DEFAULT_SORT_ORDER)
    val loader = new DateOrderedMixedCursor(calls, smss)

    def load(acc: List[ContactInfo]): List[ContactInfo] = {
      if (acc.size < maxItems) {
        loader.next match {
          case Some(next) => if (acc.exists(_.isSameContact(next))) load(acc) else load(acc ::: List(next))
          case None => acc
        }
      } else {
        acc
      }
    }
    load(pre)
  }

  class DateOrderedMixedCursor(calls: Cursor, sms: Cursor) {
    var c1 = new PeekCursor(calls, callCursorToContactInfo)
    var c2 = new PeekCursor(sms, smsCursorToContactInfo)

    def next = {
      (c1.hasCurrent, c2.hasCurrent) match {
        case (false, false) => None
        case (true, false) => c1.advance
        case (false, true) => c2.advance
        case (true, true) => if (c1.current.get.date > c2.current.get.date) {
          c1.advance
        } else {
          c2.advance
        }
      }
    }
  }
  
  class PeekCursor(c: Cursor, mapper: Cursor => ContactInfo) {
    var current = if (c.moveToFirst) loadNext else None
    def hasCurrent = current.isDefined

    def advance = {
      val prev = current
      current = loadNext
      prev
    }

    private def loadNext = {
      if (!c.isAfterLast) {
        val info = mapper(c)
        c.moveToNext
        Some(info)
      } else {
        None
      }
    }
  }
  
  private def smsCursorToContactInfo(c: Cursor) = {
    val number = c.getString(0)
    ContactInfo(number, lookupName(number), c.getLong(1), true)
  }

  private def callCursorToContactInfo(c: Cursor) = {
    ContactInfo(c.getString(0), stringOption(c.getString(1)), c.getLong(2), false)
  }

  private def lookupName(phoneNumber: String): Option[String] = {
    val lookupUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
    val cursor = managedQuery(lookupUri, Array("display_name"), null, null, null)
    if (cursor != null && cursor.moveToFirst) {
      stringOption(cursor.getString(0))
    } else {
      None
    }
  }

  private def stringOption(s: String) = if (TextUtils.isEmpty(s)) None else Some(s)

  class ItemAdapter(list: List[ContactInfo]) extends ArrayAdapter[ContactInfo](this, R.layout.item, R.id.line1, list.toArray) {

    override def getView(position: Int, convertView: View, parent: ViewGroup) = {
      val v = if (convertView != null) convertView else getLayoutInflater.inflate(R.layout.item, null)
      val info = getItem(position)
      v.findViewById(R.id.line1).asInstanceOf[TextView].setText(info.header)
      v.findViewById(R.id.line2).asInstanceOf[TextView].setText(info.extra)
      v.findViewById(R.id.latest_was_call).setVisibility(if (info.sms) View.INVISIBLE else View.VISIBLE)
      v.findViewById(R.id.latest_was_sms).setVisibility(if (info.sms) View.VISIBLE else View.INVISIBLE)
      v.setTag(info.number)
      setClick(v.findViewById(R.id.call_icon), CallOnClick, info.number)
      setClick(v.findViewById(R.id.sms_icon), SmsOnClick, info.number)
      v
    }

    private def setClick(view: View, listener: OnClickListener, number: String) {
      view.setOnClickListener(listener)
      view.setTag(number)
    }
  }

  class ContactOnClick(intent: String => Intent) extends OnClickListener {
    def onClick(v: View) = {
      val num = v.getTag.toString
      finish
      startActivity(intent(num))
    }
  }

  object CallOnClick extends ContactOnClick(num => new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", num, null)))
  object SmsOnClick extends ContactOnClick(num => new Intent(Intent.ACTION_SENDTO, Uri.fromParts("sms", num, null)))
  object ShowContactOnClick extends ContactOnClick(num => new Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT, Uri.fromParts("tel", num, null)))

  case class ContactInfo(number: String, name: Option[String], date: Long, sms: Boolean) {
    def formattedNumber = PhoneNumberUtils.formatNumber(number)
    def formattedDate = DateUtils.getRelativeTimeSpanString(date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
    def header = name.getOrElse(formattedNumber)
    def extra = formattedDate + (if (name.isDefined) "  "+formattedNumber else "")

    def isSameContact(o: ContactInfo) = {
      if (name.isDefined) name == o.name else number == o.number
    }
  }

}