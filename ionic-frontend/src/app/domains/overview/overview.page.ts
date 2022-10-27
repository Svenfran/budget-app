import { Component, OnInit } from '@angular/core';
import { GroupService } from 'src/app/services/group.service';

@Component({
  selector: 'app-overview',
  templateUrl: './overview.page.html',
  styleUrls: ['./overview.page.scss'],
})
export class OverviewPage implements OnInit {

  activeGroupName: string;

  constructor(
    private groupService: GroupService
  ) { }

  ngOnInit() {
    this.groupService.activeGroup.subscribe(group => {
      this.activeGroupName = group.name;
    })
  }

}
