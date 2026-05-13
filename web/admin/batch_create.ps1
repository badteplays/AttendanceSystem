$url = "https://attendance-system-livid-iota.vercel.app/api/users"

# ─── Filipino name pools ─────────────────────────────────────
$firstNamesMale = @("Aaron","Adrian","Aiden","Alden","Alex","Alfredo","Allan","Andrei","Angelo","Anton","Arjun","Arnel","Benedict","Benjamin","Bernard","Brandon","Bryan","Carlo","Carlos","Cedric","Christian","Clarence","Clark","Cyrus","Daniel","Darren","David","Dennis","Diego","Dominic","Dylan","Edgardo","Eduardo","Elijah","Emilio","Emmanuel","Enrique","Eric","Ethan","Felix","Fernando","Francis","Gabriel","Gerald","Giovanni","Gregorio","Harold","Harvey","Hector","Ian","Isaac","Ivan","Jacob","James","Jayden","Jerome","Jesse","Joaquin","John","Jonathan","Jose","Joshua","Julian","Justin","Karl","Keith","Kenneth","Kevin","Lance","Lawrence","Leo","Leonardo","Liam","Lorenzo","Louie","Lucas","Luis","Manuel","Marco","Marcus","Mario","Mark","Martin","Matthew","Miguel","Nathan","Neil","Nicholas","Noah","Oliver","Oscar","Paolo","Patrick","Paul","Peter","Philip","Rafael","Ramon","Randall","Raymond","Renato","Ricardo","Richard","Robert","Roberto","Rodrigo","Roland","Ronald","Ryan","Samuel","Sebastian","Simon","Stephen","Theodore","Thomas","Timothy","Tristan","Tyler","Victor","Vincent","William","Xavier","Zachary")
$firstNamesFemale = @("Abigail","Adriana","Aisha","Alessandra","Alexandra","Alicia","Alyssa","Amanda","Amara","Amber","Andrea","Angela","Angelica","Anna","Ariana","Ashley","Beatrice","Bianca","Camille","Carla","Carmen","Caroline","Cassandra","Catherine","Chloe","Christine","Claire","Claudia","Cristina","Danica","Daniela","Diana","Elena","Elizabeth","Ella","Emily","Emma","Erica","Fatima","Felicia","Gabriella","Giselle","Grace","Hannah","Helena","Isabel","Isabella","Ivy","Jade","Jana","Jasmine","Jennifer","Jessica","Joanna","Julia","Juliana","Karen","Katherine","Katrina","Kyla","Laura","Lauren","Leah","Leilani","Liana","Lily","Lisa","Lorraine","Lucia","Luna","Lydia","Mabel","Madison","Makayla","Manuela","Margarita","Maria","Mariana","Marina","Martha","Maya","Megan","Melanie","Melissa","Michelle","Mia","Monica","Natalia","Natasha","Nicole","Nina","Olivia","Patricia","Paula","Pauline","Rachel","Rebecca","Regina","Renata","Rosa","Rosalie","Sabrina","Samantha","Sarah","Selena","Serena","Sophia","Stella","Valentina","Vanessa","Veronica","Victoria","Vivian","Yvonne","Zara")
$lastNames = @("Abad","Abella","Abrigo","Acosta","Aguilar","Alcantara","Almonte","Alvarez","Andrade","Aquino","Aragon","Arroyo","Bacani","Bañares","Bautista","Bermudez","Bernardo","Borja","Buenaventura","Cabrera","Cadena","Camacho","Campos","Canlas","Cariño","Castañeda","Castillo","Castro","Catalan","Cervantes","Chavez","Concepcion","Cordero","Cruz","Cuevas","Dalisay","David","De Guzman","De Leon","De Mesa","De Villa","Del Rosario","Dela Cruz","Diaz","Dimaculangan","Dizon","Domingo","Enriquez","Escaño","Espinosa","Espiritu","Estrada","Evangelista","Fernandez","Flores","Francisco","Fuentes","Galang","Galvez","Garcia","Gomez","Gonzales","Guerrero","Gutierrez","Hernandez","Ibañez","Ilagan","Javier","Jimenez","Lacson","Lara","Laurel","Lazaro","Legaspi","Lim","Lopez","Lorenzo","Lucero","Luna","Macapagal","Magsaysay","Manalang","Manalo","Mangahas","Marquez","Martinez","Medina","Mendoza","Miranda","Montero","Morales","Navarro","Nicolas","Natividad","Ocampo","Olivares","Ortega","Pacheco","Padilla","Palma","Pangilinan","Pascual","Perez","Pineda","Ponce","Quiambao","Quinto","Ramirez","Ramos","Reyes","Rivera","Robles","Rodriguez","Romero","Rosario","Roxas","Salazar","Salvador","Sanchez","Santiago","Santos","Serrano","Sison","Solano","Soriano","Tan","Tolentino","Torres","Valdez","Valencia","Vargas","Vasquez","Velasco","Vera","Villanueva","Villarosa","Vizconde","Yu","Zamora","Zapata")

# ─── Config ───────────────────────────────────────────────────
$strands = @("STEM","HUMSS","ABM","MAWD","DIGAR")
$sections = @("301","302","303","304","305","401","402","403","404","405")
$subjects = @("English","Math","Science","Filipino","PE","ICT","Oral Communication","Practical Research","Contemporary Arts","Media & Information Literacy","Personal Development","Understanding Culture, Society & Politics","Earth & Life Science","Physical Science","General Mathematics","Statistics & Probability","Entrepreneurship","Empowerment Technologies")

$numStudents = 1300
$numTeachers = 134
$totalTarget = $numStudents + $numTeachers

# ─── Track used combos to avoid duplicates ────────────────────
$usedEmails = @{}
$rng = New-Object System.Random

function Get-UniquePerson {
    param([string]$role)
    $attempts = 0
    do {
        $attempts++
        if ($rng.Next(2) -eq 0) {
            $first = $firstNamesMale[$rng.Next($firstNamesMale.Count)]
        } else {
            $first = $firstNamesFemale[$rng.Next($firstNamesFemale.Count)]
        }
        $last = $lastNames[$rng.Next($lastNames.Count)]
        $emailBase = ($first.ToLower() + "." + ($last -replace ' ','').ToLower())
        $email = "$emailBase@school.edu"
        
        # Add number suffix if duplicate
        if ($usedEmails.ContainsKey($email)) {
            $suffix = $rng.Next(10,999)
            $email = "$emailBase$suffix@school.edu"
        }
    } while ($usedEmails.ContainsKey($email) -and $attempts -lt 50)
    
    $usedEmails[$email] = $true
    return @{name="$first $last"; email=$email}
}

# ─── Create Students ──────────────────────────────────────────
Write-Host "Creating $numStudents students..." -ForegroundColor Cyan
$studentCount = 0
$failCount = 0

for ($i = 0; $i -lt $numStudents; $i++) {
    $person = Get-UniquePerson -role "student"
    $strand = $strands[$i % $strands.Count]
    $sec = $sections[$rng.Next($sections.Count)]
    
    $body = @{
        name = $person.name
        email = $person.email
        password = "student123"
        role = "student"
        section = "$strand-$sec"
    } | ConvertTo-Json

    try {
        Invoke-RestMethod -Uri $url -Method POST -Body $body -ContentType "application/json" -ErrorAction Stop | Out-Null
        $studentCount++
        if ($studentCount % 25 -eq 0) {
            Write-Host "  Students: $studentCount / $numStudents" -ForegroundColor Green
        }
    } catch {
        $failCount++
        if ($failCount % 10 -eq 0) {
            Write-Host "  Failures: $failCount (last: $($_.Exception.Message))" -ForegroundColor Yellow
        }
        # If rate limited, wait and retry
        if ($_.Exception.Message -match "429|QUOTA|rate") {
            Write-Host "  Rate limited, waiting 10s..." -ForegroundColor Yellow
            Start-Sleep -Seconds 10
            try {
                Invoke-RestMethod -Uri $url -Method POST -Body $body -ContentType "application/json" -ErrorAction Stop | Out-Null
                $studentCount++
                $failCount--
            } catch { }
        }
    }
}
Write-Host "Students done: $studentCount / $numStudents (failed: $failCount)" -ForegroundColor Cyan

# ─── Create Teachers ──────────────────────────────────────────
Write-Host "`nCreating $numTeachers teachers..." -ForegroundColor Cyan
$teacherCount = 0
$failCount2 = 0

for ($i = 0; $i -lt $numTeachers; $i++) {
    $person = Get-UniquePerson -role "teacher"
    $subject = $subjects[$i % $subjects.Count]
    
    # Add Mr./Ms. prefix
    if ($rng.Next(2) -eq 0) {
        $person.name = "Mr. " + $person.name
    } else {
        $person.name = "Ms. " + $person.name
    }

    $body = @{
        name = $person.name
        email = $person.email
        password = "teacher123"
        role = "teacher"
        department = $subject
    } | ConvertTo-Json

    try {
        Invoke-RestMethod -Uri $url -Method POST -Body $body -ContentType "application/json" -ErrorAction Stop | Out-Null
        $teacherCount++
        if ($teacherCount % 10 -eq 0) {
            Write-Host "  Teachers: $teacherCount / $numTeachers" -ForegroundColor Green
        }
    } catch {
        $failCount2++
        if ($_.Exception.Message -match "429|QUOTA|rate") {
            Write-Host "  Rate limited, waiting 10s..." -ForegroundColor Yellow
            Start-Sleep -Seconds 10
            try {
                Invoke-RestMethod -Uri $url -Method POST -Body $body -ContentType "application/json" -ErrorAction Stop | Out-Null
                $teacherCount++
                $failCount2--
            } catch { }
        }
    }
}
Write-Host "Teachers done: $teacherCount / $numTeachers (failed: $failCount2)" -ForegroundColor Cyan

Write-Host "`n════════════════════════════════════════" -ForegroundColor White
Write-Host "TOTAL CREATED: $($studentCount + $teacherCount) / $totalTarget" -ForegroundColor Green
Write-Host "  Students: $studentCount" -ForegroundColor Blue
Write-Host "  Teachers: $teacherCount" -ForegroundColor Blue
Write-Host "════════════════════════════════════════" -ForegroundColor White
