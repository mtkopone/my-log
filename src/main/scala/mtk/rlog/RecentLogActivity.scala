package mtk.rlog
 
import android.os.Bundle
import android.app.ListActivity
import android.provider.CallLog.Calls
import android.database.Cursor
import android.view.View.OnClickListener
import android.net.Uri
import android.content.Intent
import android.text.TextUtils
import android.widget.{ArrayAdapter, TextView}
import android.view.{ViewGroup, View}
import android.telephony.PhoneNumberUtils
import android.provider.ContactsContract.PhoneLookup
import android.text.format.DateUtils

class RecentLogActivity extends ListActivity {
  val maxItems = 20

  override def onCreate(state: Bundle) {
    super.onCreate(state)
    setContentView(R.layout.list)
    setListAdapter(new ItemAdapter(reload))
  }

  private def reload = {
    val smss = managedQuery(Uri.parse("content://sms"), Array("address", "date"), "type < 3", null, "date DESC")
    val calls = managedQuery(Calls.CONTENT_URI, Array(Calls.NUMBER, Calls.CACHED_NAME, Calls.DATE), null, null, Calls.DEFAULT_SORT_ORDER)
    val loader = new DateOrderedMixedCursor(calls, smss)

    def load(acc: List[ContactInfo]): List[ContactInfo] = {
      if (acc.size < maxItems) {
        loader.next match {
          case Some(next) => if (acc.exists(_.number == next.number)) load(acc) else load(next :: acc)
          case None => acc
        }
      } else {
        acc
      }
    }
    load(List()).reverse
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
    ContactInfo(number, lookupDisplayName(number).getOrElse(null), c.getLong(1))
  }
  private def callCursorToContactInfo(c: Cursor) = {
    ContactInfo(c.getString(0), c.getString(1), c.getLong(2))
  }

  private def lookupDisplayName(phoneNumber: String): Option[String] = {
    val lookupUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
    val cursor = managedQuery(lookupUri, Array("display_name"), null, null, null)
    if (cursor.moveToFirst) {
      Some(cursor.getString(0))
    } else {
      None
    }
  }

  class ItemAdapter(list: List[ContactInfo]) extends ArrayAdapter[ContactInfo](this, R.layout.item, R.id.line1, list.toArray) {

    override def getView(position: Int, convertView: View, parent: ViewGroup) = {
      val view = if (convertView == null) getLayoutInflater.inflate(R.layout.item, null) else convertView
      val info = getItem(position)
      view.findViewById(R.id.line1).asInstanceOf[TextView].setText(info.header)
      view.findViewById(R.id.line2).asInstanceOf[TextView].setText(info.extra)
      val callIcon = view.findViewById(R.id.call_icon)
      callIcon.setOnClickListener(CallOnClick)
      callIcon.setTag(info.number)
      val smsIcon = view.findViewById(R.id.sms_icon)
      smsIcon.setOnClickListener(SmsOnClick)
      smsIcon.setTag(info.number)
      view
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

  case class ContactInfo(number: String, name: String, date: Long) {
    def formattedNumber = PhoneNumberUtils.formatNumber(number)
    def formattedDate = DateUtils.getRelativeTimeSpanString(date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
    def header = if (TextUtils.isEmpty(name)) formattedNumber else name
    def extra = formattedDate + (if (TextUtils.isEmpty(name)) "" else "  "+formattedNumber)
  }

}