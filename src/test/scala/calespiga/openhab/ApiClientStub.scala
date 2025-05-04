package calespiga.openhab

import cats.effect.IO

object ApiClientStub {
  
  def apply(): APIClient = (item: String, value: String) => IO.unit
  
  def apply(
      changeItem: (String, String) => IO[Unit]
  ): APIClient = (item: String, value: String) => changeItem(item, value)

}
