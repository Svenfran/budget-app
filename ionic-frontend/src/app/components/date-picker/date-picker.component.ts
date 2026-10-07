import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { IonDatetime } from '@ionic/angular';

@Component({
  selector: 'app-date-picker',
  templateUrl: './date-picker.component.html',
  styleUrls: ['./date-picker.component.scss'],
  standalone: false
})
export class DatePickerComponent  implements OnInit {
  @ViewChild(IonDatetime) datetime!: IonDatetime;
  @Input() selectedDate: string = ''; // Datum, das vom Eltern-Element gesetzt wird
  @Output() dateChange = new EventEmitter<string>(); // Event für das ausgewählte Datum

  constructor() { }

  ngOnInit() {}


  onDateChange(event: any) {
    const newDate = event.detail.value;  // Extrahiere den Wert aus dem Event
    this.dateChange.emit(newDate);  // Sende den neuen Wert an die Parent-Komponente
  }

  close() {
    this.datetime.cancel(true);
  }

  select() {
    this.datetime.confirm(true);
  }

}
