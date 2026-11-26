package com.github.svenfran.budgetapp.budgetappbackend.db

import liquibase.change.core.DropTableChange
import liquibase.changelog.ChangeLogParameters
import liquibase.parser.ChangeLogParserFactory
import liquibase.resource.ClassLoaderResourceAccessor
import spock.lang.Specification

/**
 * DIV-122: Der Drop der Tabelle token muss in beiden Master-Changelogs mit identischem Pfad eingebunden sein. Prod nutzt
 * einen eigenen Master, den weder die Test- noch die Dev-Läufe ausführen; ein falscher Include-Pfad fiele sonst erst
 * beim Prod-Start auf. Der Parser löst alle Includes über den Classpath auf (ohne Datenbank) und scheitert an einer
 * fehlenden Datei.
 */
class TokenTableDropChangelogSpec extends Specification {

    def "#master bindet den Drop der Tabelle token als letzten Changeset ein"() {
        given:
        def accessor = new ClassLoaderResourceAccessor()

        when:
        def changeLog = ChangeLogParserFactory.instance.getParser(master, accessor)
                .parse(master, new ChangeLogParameters(), accessor)
        def letzter = changeLog.changeSets.last()
        def changes = letzter.changes

        then:
        letzter.id == '20261126120000-1'
        letzter.author == 'sven'
        letzter.filePath == 'db/changelog/release-1.x/20261126120000-1_dropTokenTable.xml'
        changes.size() == 1
        changes[0] instanceof DropTableChange
        (changes[0] as DropTableChange).tableName == 'token'

        where:
        master << ['db/changelog/db.changelog-master.xml', 'db/changelog/db.changelog-master-prod.xml']
    }
}
